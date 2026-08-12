package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.MovimientoStockRequest;
import com.padel.rankpadel.dto.request.ProductoRequest;
import com.padel.rankpadel.dto.response.MovimientoStockResponse;
import com.padel.rankpadel.dto.response.ProductoResponse;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.entity.MovimientoStock;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.entity.Proveedor;
import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.MotivoMovimientoStock;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.repository.MovimientoStockRepository;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.ProveedorRepository;

import lombok.RequiredArgsConstructor;

/**
 * Catálogo del mostrador y control de stock. Toda variación de unidades pasa por
 * {@link #registrarMovimiento}, así el stock del producto siempre coincide con la suma
 * de sus movimientos y un faltante se puede explicar.
 */
@Service
@RequiredArgsConstructor
public class ProductoService {

    private static final int MOVIMIENTOS_EN_LA_FICHA = 40;
    private static final int COMPRAS_EN_EL_HISTORIAL = 100;

    private final ProductoRepository productoRepository;
    private final ProveedorRepository proveedorRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final GastoRepository gastoRepository;

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(String busqueda, boolean soloActivos) {
        String texto = busqueda != null && !busqueda.isBlank() ? busqueda.trim() : null;
        return productoRepository.buscar(texto, soloActivos).stream().map(this::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> conStockBajo() {
        return productoRepository.conStockBajo().stream().map(this::aResponse).toList();
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        Producto producto = Producto.builder()
                .nombre(request.getNombre().trim())
                .categoria(request.getCategoria())
                .precioVenta(request.getPrecioVenta())
                .costo(request.getCosto())
                .controlaStock(request.isControlaStock())
                .stock(0)
                .stockMinimo(request.getStockMinimo() != null ? request.getStockMinimo() : 0)
                .proveedor(proveedor(request.getProveedorId()))
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build();
        productoRepository.save(producto);

        // Las unidades que ya estaban en la vitrina entran como un movimiento más: si no,
        // el stock arrancaría con un número que no tiene ningún respaldo.
        int inicial = request.getStockInicial() != null ? request.getStockInicial() : 0;
        if (producto.isControlaStock() && inicial > 0) {
            aplicarMovimiento(producto, inicial, MotivoMovimientoStock.AJUSTE, null, null,
                    "Stock inicial al dar de alta el producto");
        }
        return aResponse(producto);
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscar(id);
        producto.setNombre(request.getNombre().trim());
        producto.setCategoria(request.getCategoria());
        producto.setPrecioVenta(request.getPrecioVenta());
        producto.setCosto(request.getCosto());
        producto.setControlaStock(request.isControlaStock());
        producto.setStockMinimo(request.getStockMinimo() != null ? request.getStockMinimo() : 0);
        producto.setProveedor(proveedor(request.getProveedorId()));
        if (request.getActivo() != null) {
            producto.setActivo(request.getActivo());
        }
        productoRepository.save(producto);
        return aResponse(producto);
    }

    /** Baja lógica: el producto sigue apareciendo en las ventas ya hechas. */
    @Transactional
    public void darDeBaja(Long id) {
        Producto producto = buscar(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    /**
     * Entrada de mercadería. Si viene el medio de pago, además registra el egreso: la
     * compra es plata que salió y tiene que pesar en la rentabilidad del mes.
     */
    @Transactional
    public ProductoResponse comprar(Long id, MovimientoStockRequest request) {
        Producto producto = buscar(id);
        exigirControlDeStock(producto, "comprar mercadería");
        // Sin el costo no se puede armar el egreso, y dejarlo pasar en silencio hacía que
        // la compra no apareciera nunca en los gastos del mes.
        if (request.getMedioPago() != null && request.getCostoUnitario() == null) {
            throw new EstadoInvalidoException(
                    "Para registrar el pago de la compra hace falta el costo por unidad.");
        }

        aplicarMovimiento(producto, request.getCantidad(), MotivoMovimientoStock.COMPRA,
                null, request.getCostoUnitario(), request.getNotas());

        // El costo del producto se actualiza al de la última compra: es lo que el club
        // tiene en la cabeza cuando mira el margen.
        if (request.getCostoUnitario() != null) {
            producto.setCosto(request.getCostoUnitario());
            productoRepository.save(producto);
        }
        if (request.getMedioPago() != null && request.getCostoUnitario() != null) {
            registrarGastoDeCompra(producto, request);
        }
        return aResponse(producto);
    }

    /**
     * Corrección tras contar la vitrina: el club dice cuántas unidades hay de verdad y el
     * sistema guarda la diferencia. Contar es más confiable que sumar y restar a mano.
     */
    @Transactional
    public ProductoResponse ajustar(Long id, int stockReal, String notas) {
        Producto producto = buscar(id);
        exigirControlDeStock(producto, "ajustar el stock");
        if (stockReal < 0) {
            throw new EstadoInvalidoException("El stock no puede ser negativo");
        }
        int diferencia = stockReal - producto.getStock();
        if (diferencia != 0) {
            aplicarMovimiento(producto, diferencia, MotivoMovimientoStock.AJUSTE, null, null, notas);
        }
        return aResponse(producto);
    }

    /** Mercadería que se rompió, se venció o se perdió. */
    @Transactional
    public ProductoResponse registrarMerma(Long id, MovimientoStockRequest request) {
        Producto producto = buscar(id);
        exigirControlDeStock(producto, "registrar una merma");
        // Dar de baja más unidades de las que hay dejaría el stock en negativo, y a partir
        // de ahí el número no se puede explicar con ningún movimiento.
        if (producto.getStock() < request.getCantidad()) {
            throw new EstadoInvalidoException("No podés dar de baja " + request.getCantidad()
                    + " unidades de \"" + producto.getNombre() + "\": quedan "
                    + producto.getStock() + ". Si el conteo no coincide, usá el ajuste de stock.");
        }
        aplicarMovimiento(producto, -request.getCantidad(), MotivoMovimientoStock.MERMA,
                null, null, request.getNotas());
        return aResponse(producto);
    }

    @Transactional(readOnly = true)
    public List<MovimientoStockResponse> movimientos(Long productoId) {
        return movimientoStockRepository
                .findDelProducto(productoId, PageRequest.of(0, MOVIMIENTOS_EN_LA_FICHA)).stream()
                .map(this::aResponse)
                .toList();
    }

    /** Historial de compras del club: qué entró, cuándo, de quién y a cuánto. */
    @Transactional(readOnly = true)
    public List<MovimientoStockResponse> compras() {
        return movimientoStockRepository.findCompras(PageRequest.of(0, COMPRAS_EN_EL_HISTORIAL)).stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Único punto por donde cambia el stock. Lo usan también las ventas y sus anulaciones.
     *
     * @param cantidad positiva si entra mercadería, negativa si sale
     */
    @Transactional
    public void aplicarMovimiento(Producto producto, int cantidad, MotivoMovimientoStock motivo,
            com.padel.rankpadel.entity.Venta venta, BigDecimal costoUnitario, String notas) {
        if (!producto.isControlaStock()) {
            return;
        }
        producto.setStock(producto.getStock() + cantidad);
        productoRepository.save(producto);

        movimientoStockRepository.save(MovimientoStock.builder()
                .producto(producto)
                .cantidad(cantidad)
                .motivo(motivo)
                .fecha(LocalDateTime.now())
                .venta(venta)
                .costoUnitario(costoUnitario)
                .registradoPor(usuarioActual())
                .notas(notas)
                .build());
    }

    @Transactional(readOnly = true)
    public Producto buscar(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    private void registrarGastoDeCompra(Producto producto, MovimientoStockRequest request) {
        BigDecimal total = request.getCostoUnitario().multiply(BigDecimal.valueOf(request.getCantidad()));
        gastoRepository.save(Gasto.builder()
                .fecha(request.getFecha() != null ? request.getFecha() : LocalDate.now())
                .categoria(CategoriaGasto.INSUMOS)
                .descripcion("Compra de " + request.getCantidad() + " x " + producto.getNombre())
                .monto(total)
                .medio(request.getMedioPago())
                .proveedor(producto.getProveedor() != null ? producto.getProveedor().getNombre() : null)
                .registradoPor(usuarioActual())
                .notas(request.getNotas())
                .creadoEn(LocalDateTime.now())
                .producto(producto)
                .build());
    }

    private void exigirControlDeStock(Producto producto, String accion) {
        if (!producto.isControlaStock()) {
            throw new EstadoInvalidoException(
                    "\"" + producto.getNombre() + "\" no lleva control de stock, así que no hace falta " + accion + ".");
        }
    }

    private Proveedor proveedor(Long proveedorId) {
        if (proveedorId == null) {
            return null;
        }
        return proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", proveedorId));
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    ProductoResponse aResponse(Producto producto) {
        Proveedor proveedor = producto.getProveedor();
        return ProductoResponse.builder()
                .id(producto.getId())
                .nombre(producto.getNombre())
                .categoria(producto.getCategoria() != null ? producto.getCategoria().name() : null)
                .precioVenta(producto.getPrecioVenta())
                .costo(producto.getCosto())
                .margenUnitario(producto.margenUnitario())
                .controlaStock(producto.isControlaStock())
                .stock(producto.getStock())
                .stockMinimo(producto.getStockMinimo())
                .necesitaReposicion(producto.necesitaReposicion())
                .proveedorId(proveedor != null ? proveedor.getId() : null)
                .proveedorNombre(proveedor != null ? proveedor.getNombre() : null)
                .activo(producto.isActivo())
                .build();
    }

    private MovimientoStockResponse aResponse(MovimientoStock movimiento) {
        Producto producto = movimiento.getProducto();
        return MovimientoStockResponse.builder()
                .id(movimiento.getId())
                .productoId(producto != null ? producto.getId() : null)
                .productoNombre(producto != null ? producto.getNombre() : null)
                .cantidad(movimiento.getCantidad())
                .motivo(movimiento.getMotivo() != null ? movimiento.getMotivo().name() : null)
                .fecha(movimiento.getFecha())
                .costoUnitario(movimiento.getCostoUnitario())
                .registradoPor(movimiento.getRegistradoPor())
                .notas(movimiento.getNotas())
                .build();
    }
}
