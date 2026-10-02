package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.MovimientoCajaRequest;
import com.padel.rankpadel.dto.response.MovimientoSueltoResponse;
import com.padel.rankpadel.entity.Cliente;
import com.padel.rankpadel.entity.MovimientoCaja;
import com.padel.rankpadel.enums.ConceptoMovimientoCaja;
import com.padel.rankpadel.enums.TipoMovimientoCaja;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.MovimientoCajaRepository;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * La plata que entra o sale del cajón y no es un turno ni una venta del kiosco: el fondo
 * con el que se abre el día, un depósito al banco, un retiro del dueño, el alquiler del
 * salón. Hasta V61 no había dónde anotarlo y lo único que quedaba era cargarlo como gasto,
 * que le ensucia la rentabilidad al club.
 */
@Service
@RequiredArgsConstructor
public class MovimientoCajaService {

    private static final Logger log = LoggerFactory.getLogger(MovimientoCajaService.class);

    private final MovimientoCajaRepository movimientoCajaRepository;
    private final ClienteRepository clienteRepository;
    private final CajaCerradaGuard cajaCerradaGuard;
    private final DisponibilidadCanchaService disponibilidadCanchaService;

    /**
     * Abre la caja de la jornada declarando el efectivo con el que arranca el cajón.
     *
     * <p>Es lo que hacía que el arqueo no cerrara nunca: el efectivo esperado arrancaba de
     * cero, y todo club deja cambio para dar vuelto, así que la diferencia salía positiva
     * por el monto del fondo todas las noches.
     */
    @Transactional
    public MovimientoSueltoResponse abrir(MovimientoCajaRequest request) {
        LocalDate jornada = disponibilidadCanchaService.fechaDeJornadaActual();
        if (movimientoCajaRepository.existsByJornadaAndConceptoAndAnuladoEnIsNull(
                jornada, ConceptoMovimientoCaja.APERTURA)) {
            throw new EstadoInvalidoException("La caja de esta jornada ya está abierta.");
        }
        request.setConcepto(ConceptoMovimientoCaja.APERTURA);
        request.setTipo(TipoMovimientoCaja.INGRESO);
        return registrar(request);
    }

    /** Si la jornada ya tiene declarado su fondo inicial. */
    @Transactional(readOnly = true)
    public boolean estaAbierta(LocalDate jornada) {
        return movimientoCajaRepository.existsByJornadaAndConceptoAndAnuladoEnIsNull(
                jornada, ConceptoMovimientoCaja.APERTURA);
    }

    @Transactional
    public MovimientoSueltoResponse registrar(MovimientoCajaRequest request) {
        TipoMovimientoCaja tipo = resolverTipo(request);
        exigirPermiso(request.getConcepto());

        // A la jornada del club, no al día de calendario, igual que cobros y ventas: lo
        // movido a la 1 AM es de la noche que arrancó ayer y va en ESE arqueo.
        LocalDate jornada = disponibilidadCanchaService.fechaDeJornadaActual();
        cajaCerradaGuard.exigirDiaAbierto(jornada);

        MovimientoCaja movimiento = movimientoCajaRepository.save(MovimientoCaja.builder()
                .jornada(jornada)
                .fecha(LocalDateTime.now())
                .tipo(tipo)
                .concepto(request.getConcepto())
                .descripcion(request.getDescripcion().trim())
                .monto(request.getMonto())
                .medio(request.getMedio())
                .cliente(cliente(request.getClienteId()))
                .registradoPor(UsuarioActual.nombre())
                .notas(request.getNotas())
                .creadoEn(LocalDateTime.now())
                .build());

        log.info("[caja] {} registró {} {} de ${} ({})", movimiento.getRegistradoPor(),
                tipo, request.getConcepto(), request.getMonto(), movimiento.getDescripcion());
        return aResponse(movimiento);
    }

    /**
     * Anular es baja lógica: la fila sale de todos los totales pero queda. Solo el dueño,
     * por lo mismo que no puede reabrir un cierre el que cobró: si el empleado puede
     * borrar de la caja un movimiento que él mismo cargó, no queda nada que auditar.
     */
    @Transactional
    public void anular(Long id, String motivo) {
        MovimientoCaja movimiento = movimientoCajaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento de caja", id));
        if (movimiento.estaAnulado()) {
            throw new EstadoInvalidoException("Ese movimiento ya está anulado.");
        }
        cajaCerradaGuard.exigirDiaAbierto(movimiento.getJornada());
        movimiento.setAnuladoEn(LocalDateTime.now());
        movimiento.setAnuladoPor(UsuarioActual.nombre());
        movimiento.setMotivoAnulacion(motivo);
        movimientoCajaRepository.save(movimiento);
        log.info("[caja] {} anuló el movimiento {} de ${}. Motivo: {}",
                movimiento.getAnuladoPor(), id, movimiento.getMonto(), motivo);
    }

    @Transactional(readOnly = true)
    public List<MovimientoSueltoResponse> listarDeLaJornada(LocalDate jornada) {
        return movimientoCajaRepository.findDeLaJornada(jornada).stream()
                .map(this::aResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovimientoSueltoResponse> listarAnuladosDeLaJornada(LocalDate jornada) {
        return movimientoCajaRepository.findAnuladosDeLaJornada(jornada).stream()
                .map(this::aResponse)
                .toList();
    }

    /** El fondo declarado al abrir. Cero si la caja de esa jornada no se abrió. */
    @Transactional(readOnly = true)
    public BigDecimal fondoInicial(LocalDate jornada) {
        return movimientoCajaRepository
                .findDeLaJornadaPorConcepto(jornada, ConceptoMovimientoCaja.APERTURA).stream()
                .map(MovimientoCaja::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * El tipo que corresponde. Los conceptos con un único sentido posible lo imponen: un
     * "retiro del dueño" que sume plata al cajón es un movimiento que no existe.
     */
    private TipoMovimientoCaja resolverTipo(MovimientoCajaRequest request) {
        TipoMovimientoCaja forzado = request.getConcepto().tipoForzado();
        if (forzado != null) {
            if (request.getTipo() != null && request.getTipo() != forzado) {
                throw new EstadoInvalidoException("Un movimiento de tipo "
                        + request.getConcepto() + " siempre es " + forzado.name().toLowerCase() + ".");
            }
            return forzado;
        }
        if (request.getTipo() == null) {
            throw new EstadoInvalidoException("Indicá si la plata entra o sale.");
        }
        return request.getTipo();
    }

    /**
     * Los movimientos que sirven para explicar un faltante son solo del dueño. Esto no
     * puede estar en {@code SecurityConfig} porque depende del cuerpo del pedido y no de
     * la ruta.
     */
    private void exigirPermiso(ConceptoMovimientoCaja concepto) {
        if (concepto.exigeDuenio() && !UsuarioActual.esDuenio()) {
            throw new EstadoInvalidoException(
                    "Ese movimiento lo tiene que registrar el dueño.");
        }
    }

    private Cliente cliente(Long clienteId) {
        if (clienteId == null) {
            return null;
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", clienteId));
    }

    /** Lo usa tambien CajaService para armar el detalle del arqueo. */
    public MovimientoSueltoResponse aResponse(MovimientoCaja movimiento) {
        return MovimientoSueltoResponse.builder()
                .id(movimiento.getId())
                .jornada(movimiento.getJornada())
                .fecha(movimiento.getFecha())
                .tipo(movimiento.getTipo() != null ? movimiento.getTipo().name() : null)
                .concepto(movimiento.getConcepto() != null ? movimiento.getConcepto().name() : null)
                .descripcion(movimiento.getDescripcion())
                .monto(movimiento.getMonto())
                .medio(movimiento.getMedio() != null ? movimiento.getMedio().name() : null)
                .clienteId(movimiento.getCliente() != null ? movimiento.getCliente().getId() : null)
                .clienteNombre(movimiento.getCliente() != null ? movimiento.getCliente().getNombre() : null)
                .registradoPor(movimiento.getRegistradoPor())
                .notas(movimiento.getNotas())
                .anuladoEn(movimiento.getAnuladoEn())
                .anuladoPor(movimiento.getAnuladoPor())
                .motivoAnulacion(movimiento.getMotivoAnulacion())
                .build();
    }
}
