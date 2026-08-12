package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.VentaRequest;
import com.padel.rankpadel.dto.response.VentaResponse;
import com.padel.rankpadel.entity.Cliente;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.Venta;
import com.padel.rankpadel.entity.VentaItem;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.MotivoMovimientoStock;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.VentaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ventas del mostrador: pelotas, bebidas, alquiler de paletas. Descuentan stock y entran
 * al cierre de caja del día por el mismo camino que el cobro de un turno.
 */
@Service
@RequiredArgsConstructor
public class VentaService {

    private static final Logger log = LoggerFactory.getLogger(VentaService.class);

    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final CobroRepository cobroRepository;
    private final ProductoService productoService;
    private final CajaCerradaGuard cajaCerradaGuard;

    @Transactional
    public VentaResponse registrar(VentaRequest request) {
        cajaCerradaGuard.exigirDiaAbierto(LocalDate.now());
        Reserva reserva = reserva(request.getReservaId());
        // Sin medio de pago la venta va a la cuenta del turno y se cobra al final, junto
        // con la cancha. Sin turno tampoco, sería plata que se pierde de vista.
        if (request.getMedio() == null && reserva == null) {
            throw new EstadoInvalidoException(
                    "Elegí cómo pagó, o cargá el consumo a un turno para cobrarlo al final.");
        }
        if (reserva != null && request.getMedio() == null) {
            validarTurnoCobrable(reserva);
        }

        // Si la venta va a un turno, la ficha sale de ahí. Sin esto, lo que el grupo
        // consumió no aparecía nunca en el historial de compras de ese cliente.
        Cliente cliente = cliente(request.getClienteId());
        if (cliente == null && reserva != null) {
            cliente = reserva.getCliente();
        }

        Venta venta = Venta.builder()
                .fecha(LocalDateTime.now())
                .medio(request.getMedio())
                .cliente(cliente)
                .reserva(reserva)
                .registradoPor(usuarioActual())
                .notas(request.getNotas())
                .items(new ArrayList<>())
                .total(BigDecimal.ZERO)
                .build();

        // El mismo producto puede venir en varios renglones, porque el mostrador lo agrega
        // de a uno. Se suman ANTES de validar: si no, cada renglón se comparaba contra el
        // stock entero y entre todos se vendía más de lo que había en la heladera.
        Map<Long, Integer> pedidas = new LinkedHashMap<>();
        for (VentaRequest.Item pedido : request.getItems()) {
            pedidas.merge(pedido.getProductoId(), pedido.getCantidad(), Integer::sum);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> pedido : pedidas.entrySet()) {
            Producto producto = productoService.buscar(pedido.getKey());
            validarDisponible(producto, pedido.getValue());

            VentaItem item = VentaItem.builder()
                    .venta(venta)
                    .producto(producto)
                    // Precio y costo se congelan: una actualización de la lista no puede
                    // cambiar lo que ya se vendió ni el margen con el que se vendió.
                    .precioUnitario(producto.getPrecioVenta())
                    .costoUnitario(producto.getCosto())
                    .cantidad(pedido.getValue())
                    .build();
            venta.getItems().add(item);
            total = total.add(item.subtotal());
        }

        venta.setTotal(total);
        ventaRepository.save(venta);

        for (VentaItem item : venta.getItems()) {
            productoService.aplicarMovimiento(item.getProducto(), -item.getCantidad(),
                    MotivoMovimientoStock.VENTA, venta, null, null);
        }
        return aResponse(venta);
    }

    @Transactional(readOnly = true)
    public List<VentaResponse> listarDelDia(LocalDate fecha) {
        return ventaRepository
                .findDelDiaConItems(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()).stream()
                .map(this::aResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VentaResponse> listarDeReserva(Long reservaId) {
        return ventaRepository.findVigentesDeReserva(reservaId).stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Anula una venta cargada por error: la mercadería vuelve al stock y la plata sale de
     * la caja del día. Es baja lógica —la fila queda con el autor y el motivo—, así el
     * cierre de un día pasado no cambia solo y se puede auditar qué se anuló.
     *
     * <p>Si la venta estaba anotada en la cuenta de un turno y ese turno ya se cobró, la
     * plata entró de verdad: anularla dejaría un cobro sin renglón que lo explique, así
     * que se exige confirmación explícita.
     */
    @Transactional
    public VentaResponse anular(Long id, String motivo, boolean confirmado) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta", id));
        if (venta.estaAnulada()) {
            throw new EstadoInvalidoException("Esa venta ya está anulada");
        }
        cajaCerradaGuard.exigirDiaAbierto(venta.getFecha().toLocalDate());
        if (!confirmado && yaSeCobro(venta)) {
            throw new EstadoInvalidoException(
                    "Este consumo ya se cobró junto con el turno. Si lo anulás, la plata cobrada"
                            + " queda sin un renglón que la explique: confirmá para seguir.");
        }

        for (VentaItem item : venta.getItems()) {
            productoService.aplicarMovimiento(item.getProducto(), item.getCantidad(),
                    MotivoMovimientoStock.ANULACION, venta, null, "Anulación de la venta " + id);
        }

        venta.setAnuladoEn(LocalDateTime.now());
        venta.setAnuladoPor(usuarioActual());
        venta.setMotivoAnulacion(motivo != null && !motivo.isBlank() ? motivo.trim() : null);
        ventaRepository.save(venta);

        log.info("[caja] {} anuló la venta {} de ${}", usuarioActual(), id, venta.getTotal());
        return aResponse(venta);
    }

    /**
     * Un consumo a cuenta ya cobrado: la venta no tiene medio propio (iba al turno) y el
     * turno registra cobros. No hace falta afinar más — alcanza para pedir confirmación.
     */
    private boolean yaSeCobro(Venta venta) {
        return venta.getMedio() == null
                && venta.getReserva() != null
                && cobroRepository.totalCobradoDe(venta.getReserva().getId())
                        .compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Un turno cancelado o vencido ya no tiene cuenta contra la que anotar: si el grupo
     * igual consumió, hay que cobrarlo en el momento como venta suelta.
     */
    private void validarTurnoCobrable(Reserva reserva) {
        EstadoReserva estado = reserva.getEstado();
        if (estado != EstadoReserva.CONFIRMADA && estado != EstadoReserva.FINALIZADA
                && estado != EstadoReserva.NO_SHOW) {
            throw new EstadoInvalidoException(
                    "El turno está " + estado.name().toLowerCase()
                            + ": cobrá el consumo en el momento en vez de anotarlo en la cuenta.");
        }
    }

    private void validarDisponible(Producto producto, int cantidad) {
        if (!producto.isActivo()) {
            throw new EstadoInvalidoException("\"" + producto.getNombre() + "\" está dado de baja");
        }
        if (producto.isControlaStock() && producto.getStock() < cantidad) {
            throw new EstadoInvalidoException("No hay stock suficiente de \"" + producto.getNombre()
                    + "\": quedan " + producto.getStock() + ".");
        }
    }

    private Cliente cliente(Long clienteId) {
        if (clienteId == null) {
            return null;
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", clienteId));
    }

    private Reserva reserva(Long reservaId) {
        if (reservaId == null) {
            return null;
        }
        return reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", reservaId));
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    VentaResponse aResponse(Venta venta) {
        List<VentaResponse.Item> items = venta.getItems().stream()
                .map(item -> VentaResponse.Item.builder()
                        .productoId(item.getProducto() != null ? item.getProducto().getId() : null)
                        .productoNombre(item.getProducto() != null ? item.getProducto().getNombre() : null)
                        .cantidad(item.getCantidad())
                        .precioUnitario(item.getPrecioUnitario())
                        .subtotal(item.subtotal())
                        .build())
                .toList();

        Cliente cliente = venta.getCliente();
        return VentaResponse.builder()
                .id(venta.getId())
                .fecha(venta.getFecha())
                .total(venta.getTotal())
                .medio(venta.getMedio() != null ? venta.getMedio().name() : null)
                .clienteId(cliente != null ? cliente.getId() : null)
                .clienteNombre(cliente != null ? cliente.getNombre() : null)
                .reservaId(venta.getReserva() != null ? venta.getReserva().getId() : null)
                .registradoPor(venta.getRegistradoPor())
                .notas(venta.getNotas())
                .items(items)
                .detalle(items.stream()
                        .map(item -> item.getCantidad() + " x " + item.getProductoNombre())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""))
                .anuladoEn(venta.getAnuladoEn())
                .anuladoPor(venta.getAnuladoPor())
                .motivoAnulacion(venta.getMotivoAnulacion())
                .build();
    }
}
