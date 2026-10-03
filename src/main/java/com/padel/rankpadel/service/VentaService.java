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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.VentaRequest;
import com.padel.rankpadel.dto.response.VentaResponse;
import com.padel.rankpadel.entity.Cliente;
import com.padel.rankpadel.entity.ConfiguracionSede;
import com.padel.rankpadel.entity.PresentacionProducto;
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
import com.padel.rankpadel.repository.ConfiguracionSedeRepository;
import com.padel.rankpadel.repository.PresentacionProductoRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.VentaRepository;

import com.padel.rankpadel.util.UsuarioActual;

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
    private final PresentacionProductoRepository presentacionProductoRepository;
    private final DisponibilidadCanchaService disponibilidadCanchaService;
    private final ConfiguracionSedeRepository configuracionSedeRepository;

    @Transactional
    public VentaResponse registrar(VentaRequest request) {
        // A la jornada del club, no al día de calendario: lo vendido a la 1 AM es de la
        // noche que arrancó ayer y va en ese arqueo (ver V55).
        LocalDate jornada = disponibilidadCanchaService.fechaDeJornadaActual();
        cajaCerradaGuard.exigirDiaAbierto(jornada);
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
                .jornada(jornada)
                .medio(request.getMedio())
                .cliente(cliente)
                .reserva(reserva)
                .registradoPor(UsuarioActual.nombre())
                .notas(request.getNotas())
                .items(new ArrayList<>())
                .total(BigDecimal.ZERO)
                .build();

        // El mismo producto y la misma presentación pueden venir en varios renglones,
        // porque el mostrador los agrega de a uno. Se suman ANTES de validar: si no, cada
        // renglón se comparaba contra el stock entero y entre todos se vendía más de lo
        // que había en la heladera.
        //
        // La clave incluye la presentación, porque "1 tubo" y "2 sueltas" son dos
        // renglones distintos del mismo producto. Pero el STOCK se valida sumando las
        // unidades base de todos ellos: un tubo y dos sueltas son cinco pelotas, y
        // validar cada renglón contra el stock por separado dejaría vender de más otra
        // vez, que es justo el bug que arregló V52.
        Map<ClaveRenglon, Integer> pedidas = new LinkedHashMap<>();
        for (VentaRequest.Item pedido : request.getItems()) {
            pedidas.merge(new ClaveRenglon(pedido.getProductoId(), pedido.getPresentacionId()),
                    pedido.getCantidad(), Integer::sum);
        }

        Map<Long, Producto> productos = new LinkedHashMap<>();
        Map<Long, Integer> unidadesBasePorProducto = new LinkedHashMap<>();
        Map<ClaveRenglon, PresentacionProducto> presentaciones = new LinkedHashMap<>();
        for (Map.Entry<ClaveRenglon, Integer> pedido : pedidas.entrySet()) {
            Producto producto = productos.computeIfAbsent(pedido.getKey().productoId(),
                    productoService::buscar);
            PresentacionProducto presentacion = presentacionDe(producto, pedido.getKey().presentacionId());
            presentaciones.put(pedido.getKey(), presentacion);
            int factor = presentacion != null ? presentacion.getUnidades() : 1;
            unidadesBasePorProducto.merge(producto.getId(), pedido.getValue() * factor, Integer::sum);
        }
        for (Map.Entry<Long, Integer> porProducto : unidadesBasePorProducto.entrySet()) {
            validarDisponible(productos.get(porProducto.getKey()), porProducto.getValue());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<ClaveRenglon, Integer> pedido : pedidas.entrySet()) {
            Producto producto = productos.get(pedido.getKey().productoId());
            PresentacionProducto presentacion = presentaciones.get(pedido.getKey());
            int factor = presentacion != null ? presentacion.getUnidades() : 1;
            BigDecimal costoBase = producto.costoDeValuacion();

            VentaItem item = VentaItem.builder()
                    .venta(venta)
                    .producto(producto)
                    .presentacion(presentacion)
                    .factor(factor)
                    // Precio y costo se congelan: una actualización de la lista no puede
                    // cambiar lo que ya se vendió ni el margen con el que se vendió. El
                    // costo va POR PRESENTACIÓN: el de un tubo es el de tres pelotas, o
                    // el margen del tubo se mediría contra el costo de una sola.
                    .precioUnitario(presentacion != null ? presentacion.getPrecioVenta() : producto.getPrecioVenta())
                    .costoUnitario(costoBase != null
                            ? costoBase.multiply(BigDecimal.valueOf(factor))
                            : null)
                    .cantidad(pedido.getValue())
                    .build();
            venta.getItems().add(item);
            total = total.add(item.subtotal());
        }

        BigDecimal descuento = repartirDescuento(venta.getItems(), total, request);
        venta.setDescuento(descuento);
        venta.setMotivoDescuento(descuento.signum() > 0 ? request.getMotivoDescuento() : null);
        venta.setTotal(total.subtract(descuento));
        ventaRepository.save(venta);

        for (VentaItem item : venta.getItems()) {
            productoService.aplicarMovimiento(item.getProducto(), -item.unidadesBase(),
                    MotivoMovimientoStock.VENTA, venta, null, null);
        }
        return aResponse(venta);
    }

    /**
     * Reparte el descuento de la venta entre sus renglones y devuelve lo descontado.
     *
     * <p>La persona lo piensa sobre el total —"quedate con cinco mil"— pero se guarda por
     * renglón, porque el invariante que importa es que {@code venta.total} sea la suma de
     * sus renglones. Un descuento suelto en la cabecera lo rompe: el ranking de productos
     * suma renglones y la facturación del mes suma totales, y los dos números dejarían de
     * cerrar.
     *
     * <p>Se reparte en proporción a lo que vale cada renglón y el resto del redondeo va al
     * más caro, así la suma da exacta hasta el peso. Repartirlo en partes iguales le haría
     * perder más margen al producto barato, que es al revés de lo que el club quiere.
     */
    private BigDecimal repartirDescuento(List<VentaItem> items, BigDecimal bruto,
            VentaRequest request) {
        BigDecimal descuento = request.getDescuento();
        if (descuento == null || descuento.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (descuento.signum() < 0) {
            throw new EstadoInvalidoException("El descuento no puede ser negativo.");
        }
        if (descuento.compareTo(bruto) > 0) {
            throw new EstadoInvalidoException("El descuento no puede ser mayor que la venta ("
                    + bruto + ").");
        }
        // Sin motivo, un margen flojo tres meses después no se puede explicar, y la
        // bonificación es justamente lo que hay que poder revisar.
        if (request.getMotivoDescuento() == null || request.getMotivoDescuento().isBlank()) {
            throw new EstadoInvalidoException("Decí por qué se bonifica.");
        }
        exigirDentroDelTope(descuento, bruto);

        BigDecimal repartido = BigDecimal.ZERO;
        VentaItem masCaro = items.get(0);
        for (VentaItem item : items) {
            if (item.bruto().compareTo(masCaro.bruto()) > 0) {
                masCaro = item;
            }
            BigDecimal parte = descuento.multiply(item.bruto())
                    .divide(bruto, 2, RoundingMode.HALF_UP);
            item.setDescuento(parte);
            repartido = repartido.add(parte);
        }
        masCaro.setDescuento(masCaro.getDescuento().add(descuento.subtract(repartido)));
        return descuento;
    }

    /**
     * El mostrador bonifica hasta donde el dueño lo dejó. Sin configurar es cero: el club
     * que no decidió dar esa atribución no la dio, y arrancar permitiendo sería decidir
     * por él. El dueño no tiene tope.
     */
    private void exigirDentroDelTope(BigDecimal descuento, BigDecimal bruto) {
        if (UsuarioActual.esDuenio()) {
            return;
        }
        Integer tope = configuracionSedeRepository.findById(1L)
                .map(ConfiguracionSede::getDescuentoMaximoMostrador)
                .orElse(0);
        int maximo = tope != null ? tope : 0;
        BigDecimal permitido = bruto.multiply(BigDecimal.valueOf(maximo))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (descuento.compareTo(permitido) > 0) {
            throw new EstadoInvalidoException(maximo == 0
                    ? "Los descuentos los autoriza el dueño."
                    : "Podés bonificar hasta el " + maximo + "%. Para más, lo autoriza el dueño.");
        }
    }

    /** Producto + presentación: lo que define un renglón de la venta. */
    private record ClaveRenglon(Long productoId, Long presentacionId) {
    }

    /**
     * La presentación elegida, validando que sea de ESE producto y esté activa: un id de
     * otro producto cobraría el precio equivocado y descontaría del stock equivocado.
     */
    private PresentacionProducto presentacionDe(Producto producto, Long presentacionId) {
        if (presentacionId == null) {
            return null;
        }
        PresentacionProducto presentacion = presentacionProductoRepository.findById(presentacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentación", presentacionId));
        if (presentacion.getProducto() == null
                || !presentacion.getProducto().getId().equals(producto.getId())) {
            throw new EstadoInvalidoException("Esa presentación no es de \"" + producto.getNombre() + "\".");
        }
        if (!presentacion.isActivo()) {
            throw new EstadoInvalidoException(
                    "La presentación \"" + presentacion.getNombre() + "\" está dada de baja.");
        }
        return presentacion;
    }

    @Transactional(readOnly = true)
    public List<VentaResponse> listarDelDia(LocalDate jornada) {
        return ventaRepository.findDeLaJornadaConItems(jornada).stream()
                .map(this::aResponse)
                .toList();
    }

    /** La jornada que el club está atendiendo: la que abre el mostrador si no elige otra. */
    @Transactional(readOnly = true)
    public LocalDate jornadaActual() {
        return disponibilidadCanchaService.fechaDeJornadaActual();
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
        cajaCerradaGuard.exigirDiaAbierto(venta.getJornada());
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
        venta.setAnuladoPor(UsuarioActual.nombre());
        venta.setMotivoAnulacion(motivo != null && !motivo.isBlank() ? motivo.trim() : null);
        ventaRepository.save(venta);

        log.info("[caja] {} anuló la venta {} de ${}", UsuarioActual.nombre(), id, venta.getTotal());
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

    VentaResponse aResponse(Venta venta) {
        List<VentaResponse.Item> items = venta.getItems().stream()
                .map(item -> VentaResponse.Item.builder()
                        .productoId(item.getProducto() != null ? item.getProducto().getId() : null)
                        .productoNombre(item.getProducto() != null ? item.getProducto().getNombre() : null)
                        .presentacionNombre(item.getPresentacion() != null ? item.getPresentacion().getNombre() : null)
                        .factor(item.getFactor())
                        .cantidad(item.getCantidad())
                        .precioUnitario(item.getPrecioUnitario())
                        .descuento(item.getDescuento())
                        .subtotal(item.subtotal())
                        .build())
                .toList();

        Cliente cliente = venta.getCliente();
        return VentaResponse.builder()
                .id(venta.getId())
                .fecha(venta.getFecha())
                .total(venta.getTotal())
                .descuento(venta.getDescuento())
                .motivoDescuento(venta.getMotivoDescuento())
                .medio(venta.getMedio() != null ? venta.getMedio().name() : null)
                .clienteId(cliente != null ? cliente.getId() : null)
                .clienteNombre(cliente != null ? cliente.getNombre() : null)
                .reservaId(venta.getReserva() != null ? venta.getReserva().getId() : null)
                .registradoPor(venta.getRegistradoPor())
                .notas(venta.getNotas())
                .items(items)
                .detalle(items.stream()
                        // Con el pack adelante: dos renglones del mismo producto que
                        // dicen lo mismo no se pueden distinguir en la caja.
                        .map(item -> item.getCantidad() + " x " + item.getProductoNombre()
                                + (item.getPresentacionNombre() != null
                                        ? " (" + item.getPresentacionNombre() + ")"
                                        : ""))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""))
                .anuladoEn(venta.getAnuladoEn())
                .anuladoPor(venta.getAnuladoPor())
                .motivoAnulacion(venta.getMotivoAnulacion())
                .build();
    }
}
