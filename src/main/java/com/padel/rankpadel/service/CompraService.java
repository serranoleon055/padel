package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.CompraRequest;
import com.padel.rankpadel.dto.request.GastoRequest;
import com.padel.rankpadel.dto.response.CompraResponse;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.entity.DocumentoCompra;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.entity.MovimientoStock;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.entity.Proveedor;
import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.DocumentoCompraRepository;
import com.padel.rankpadel.repository.MovimientoStockRepository;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.ProveedorRepository;
import com.padel.rankpadel.util.NombreEnum;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * Compras a proveedor, tal como vino el papel: un comprobante con varios productos.
 *
 * <p>Antes la compra era de a un producto, así que un remito de doce eran doce llamadas y
 * doce gastos separados en la rentabilidad del mes, imposibles de reconciliar contra el
 * comprobante que el club tiene en la mano.
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    private static final Logger log = LoggerFactory.getLogger(CompraService.class);

    private final DocumentoCompraRepository documentoCompraRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final ProductoService productoService;
    private final GastoService gastoService;
    private final CajaCerradaGuard cajaCerradaGuard;
    private final DisponibilidadCanchaService disponibilidadCanchaService;

    @Transactional
    public CompraResponse registrar(CompraRequest request) {
        Proveedor proveedor = proveedorRepository.findById(request.getProveedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.getProveedorId()));
        String numero = request.getNumero().trim();
        exigirComprobanteLibre(proveedor, request, numero);

        LocalDate jornada = disponibilidadCanchaService.fechaDeJornadaActual();
        cajaCerradaGuard.exigirDiaAbierto(jornada);

        // Un mismo producto en dos renglones del comprobante se suma: es el mismo ingreso
        // de mercadería, y partido en dos el costo promedio se calcularía en dos pasos
        // con un stock intermedio que nunca existió.
        Map<Long, Renglon> renglones = new LinkedHashMap<>();
        for (CompraRequest.Item item : request.getItems()) {
            renglones.merge(item.getProductoId(),
                    Renglon.de(item.getCantidad(), item.getCostoUnitario()),
                    Renglon::mas);
        }

        BigDecimal total = renglones.values().stream()
                .map(Renglon::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // El egreso primero: si la jornada está arqueada o la fecha es futura, la compra
        // no entra y el stock tampoco se mueve. Mismo orden que la compra suelta.
        Gasto gasto = gastoService.registrarEntidad(
                pedidoDeGasto(request, proveedor, numero, total), null, true);
        gasto.setProveedorRef(proveedor);

        DocumentoCompra compra = documentoCompraRepository.save(DocumentoCompra.builder()
                .proveedor(proveedor)
                .tipoComprobante(request.getTipoComprobante())
                .numero(numero)
                .fecha(request.getFecha())
                .jornada(jornada)
                .total(total)
                .medio(request.getMedio())
                .gasto(gasto)
                .registradoPor(UsuarioActual.nombre())
                .notas(request.getNotas())
                .creadoEn(LocalDateTime.now())
                .build());
        gasto.setDocumentoCompra(compra);

        // El movimiento se fecha cuando entró la mercadería, no cuando se cargó: una
        // compra con fecha vieja tiene que quedar en el kardex en esa fecha.
        LocalDateTime cuando = instanteDe(request.getFecha());
        for (Map.Entry<Long, Renglon> renglon : renglones.entrySet()) {
            Producto producto = productoRepository.findById(renglon.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", renglon.getKey()));
            productoService.ingresarMercaderia(producto, renglon.getValue().cantidad(),
                    renglon.getValue().costoUnitario(), cuando, compra, null);
        }

        log.info("[compras] {} cargó {} {} de {} por ${} ({})", compra.getRegistradoPor(),
                compra.getTipoComprobante(), numero, proveedor.getNombre(), total,
                compra.esACredito() ? "a cuenta" : compra.getMedio());
        return aResponse(compra);
    }

    /**
     * Anula la compra entera: revierte el stock de todos sus renglones y anula el egreso.
     *
     * <p>Los movimientos originales NO se borran: se emiten compensatorios. El stock de un
     * producto tiene que seguir siendo la suma de sus movimientos (V48), y un renglón que
     * desaparece deja un faltante que nadie puede explicar.
     */
    @Transactional
    public void anular(Long id, String motivo) {
        DocumentoCompra compra = buscar(id);
        if (compra.estaAnulado()) {
            throw new EstadoInvalidoException("Esa compra ya está anulada.");
        }
        cajaCerradaGuard.exigirDiaAbierto(compra.getJornada());

        for (MovimientoStock movimiento : movimientoStockRepository.findDeLaCompra(id)) {
            productoService.revertirIngreso(movimiento.getProducto(), movimiento.getCantidad(),
                    compra, "Anulación de " + NombreEnum.enMinuscula(compra.getTipoComprobante()) + " " + compra.getNumero());
        }
        if (compra.getGasto() != null && !compra.getGasto().estaAnulado()) {
            gastoService.anular(compra.getGasto().getId(),
                    "Compra anulada" + (motivo != null ? ": " + motivo : ""));
        }

        compra.setAnuladoEn(LocalDateTime.now());
        compra.setAnuladoPor(UsuarioActual.nombre());
        compra.setMotivoAnulacion(motivo);
        documentoCompraRepository.save(compra);
        log.info("[compras] {} anuló {} {} de ${}. Motivo: {}", compra.getAnuladoPor(),
                compra.getTipoComprobante(), compra.getNumero(), compra.getTotal(), motivo);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CompraResponse> listar(Long proveedorId, int pagina, int tamanio) {
        Page<DocumentoCompra> compras = documentoCompraRepository
                .buscar(proveedorId, PageRequest.of(pagina, tamanio));
        return PagedResponse.<CompraResponse>builder()
                .contenido(compras.getContent().stream().map(this::aResponse).toList())
                .totalElementos(compras.getTotalElements())
                .totalPaginas(compras.getTotalPages())
                .pagina(pagina)
                .tamanio(tamanio)
                .esPrimera(compras.isFirst())
                .esUltima(compras.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public CompraResponse obtener(Long id) {
        return aResponse(buscar(id));
    }

    private DocumentoCompra buscar(Long id) {
        return documentoCompraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compra", id));
    }

    /**
     * El mismo comprobante del mismo proveedor cargado dos veces es el error de carga más
     * común, y duplica el stock y el egreso sin que nadie lo note.
     */
    private void exigirComprobanteLibre(Proveedor proveedor, CompraRequest request, String numero) {
        if (documentoCompraRepository.existsByProveedorIdAndTipoComprobanteAndNumeroAndAnuladoEnIsNull(
                proveedor.getId(), request.getTipoComprobante(), numero)) {
            throw new EstadoInvalidoException("Ya cargaste el " + NombreEnum.enMinuscula(request.getTipoComprobante())
                    + " " + numero + " de " + proveedor.getNombre() + ".");
        }
    }

    private GastoRequest pedidoDeGasto(CompraRequest request, Proveedor proveedor, String numero,
            BigDecimal total) {
        GastoRequest pedido = new GastoRequest();
        pedido.setFecha(request.getFecha());
        pedido.setCategoria(CategoriaGasto.INSUMOS);
        pedido.setDescripcion(NombreEnum.capitalizado(request.getTipoComprobante()) + " " + numero + " · " + proveedor.getNombre());
        pedido.setMonto(total);
        // Null = a cuenta corriente. El egreso se registra igual porque la mercadería ya
        // entró y pesa en la rentabilidad, pero no sale del cajón hasta que se le pague.
        pedido.setMedio(request.getMedio());
        pedido.setProveedor(proveedor.getNombre());
        pedido.setNotas(request.getNotas());
        return pedido;
    }

    private LocalDateTime instanteDe(LocalDate fecha) {
        return fecha == null || fecha.isEqual(LocalDate.now())
                ? LocalDateTime.now()
                : fecha.atStartOfDay();
    }

    private CompraResponse aResponse(DocumentoCompra compra) {
        List<CompraResponse.Item> items = new ArrayList<>();
        for (MovimientoStock movimiento : movimientoStockRepository.findDeLaCompra(compra.getId())) {
            // Los compensatorios de una anulación no son renglones del comprobante.
            if (movimiento.getCantidad() <= 0) {
                continue;
            }
            BigDecimal costo = movimiento.getCostoUnitario();
            items.add(CompraResponse.Item.builder()
                    .productoId(movimiento.getProducto() != null ? movimiento.getProducto().getId() : null)
                    .productoNombre(movimiento.getProducto() != null ? movimiento.getProducto().getNombre() : null)
                    .cantidad(movimiento.getCantidad())
                    .costoUnitario(costo)
                    .subtotal(costo != null
                            ? costo.multiply(BigDecimal.valueOf(movimiento.getCantidad()))
                            : BigDecimal.ZERO)
                    .build());
        }
        Proveedor proveedor = compra.getProveedor();
        return CompraResponse.builder()
                .id(compra.getId())
                .proveedorId(proveedor != null ? proveedor.getId() : null)
                .proveedorNombre(proveedor != null ? proveedor.getNombre() : null)
                .tipoComprobante(compra.getTipoComprobante() != null ? compra.getTipoComprobante().name() : null)
                .numero(compra.getNumero())
                .fecha(compra.getFecha())
                .jornada(compra.getJornada())
                .total(compra.getTotal())
                .medio(compra.getMedio() != null ? compra.getMedio().name() : null)
                .quedaACuenta(compra.esACredito())
                .registradoPor(compra.getRegistradoPor())
                .notas(compra.getNotas())
                .items(items)
                .anuladoEn(compra.getAnuladoEn())
                .anuladoPor(compra.getAnuladoPor())
                .motivoAnulacion(compra.getMotivoAnulacion())
                .build();
    }

    /**
     * Un renglón del comprobante, ya sumado si el producto venía repetido.
     *
     * <p>Guarda el SUBTOTAL y no el costo unitario: si el mismo producto viene en dos
     * renglones a precios distintos —pasa, cuando el proveedor factura dos lotes—, quedarse
     * con uno de los dos precios y multiplicarlo por la cantidad total daría un total que
     * no es el del comprobante. El costo unitario se deriva al final, ponderado.
     */
    private record Renglon(int cantidad, BigDecimal subtotal) {

        static Renglon de(int cantidad, BigDecimal costoUnitario) {
            return new Renglon(cantidad, costoUnitario.multiply(BigDecimal.valueOf(cantidad)));
        }

        Renglon mas(Renglon otro) {
            return new Renglon(cantidad + otro.cantidad, subtotal.add(otro.subtotal));
        }

        BigDecimal costoUnitario() {
            return subtotal.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
        }
    }
}
