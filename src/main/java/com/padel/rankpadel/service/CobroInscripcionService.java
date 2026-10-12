package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.PagoJugadorRequest;
import com.padel.rankpadel.dto.response.CuentaInscripcionesResponse;
import com.padel.rankpadel.dto.response.CuentaInscripcionesResponse.CuentaJugador;
import com.padel.rankpadel.dto.response.CuentaInscripcionesResponse.CuentaPareja;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.PagoInscripcion;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.PagoInscripcionRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.util.Pesos;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Quién pagó la inscripción y quién debe, jugador por jugador.
 *
 * <p>Cada jugador debe el costo congelado en su pareja. Se le puede cobrar en partes y
 * por cualquier medio; la seña que entra por Mercado Pago se reparte mitad y mitad entre
 * los dos, porque se pagó por la pareja. Los pagos no se borran: se anulan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CobroInscripcionService {

    private final PagoInscripcionRepository pagoInscripcionRepository;
    private final ParejaRepository parejaRepository;
    private final TorneoRepository torneoRepository;

    @Transactional(readOnly = true)
    public CuentaInscripcionesResponse cuenta(Long torneoId) {
        if (!torneoRepository.existsById(torneoId)) {
            throw new ResourceNotFoundException("Torneo", torneoId);
        }
        List<Pareja> parejas = parejaRepository.findByTorneoId(torneoId);
        Map<Long, List<PagoInscripcion>> pagosPorPareja = pagoInscripcionRepository
                .findByParejaTorneoIdOrderByFechaAscIdAsc(torneoId).stream()
                .collect(Collectors.groupingBy(pago -> pago.getPareja().getId()));

        List<CuentaPareja> cuentas = parejas.stream()
                .sorted(Comparator.comparing((Pareja p) -> p.getCategoria() != null ? p.getCategoria().getNombre() : "")
                        .thenComparing(Pareja::getId))
                .map(pareja -> cuentaPareja(pareja, pagosPorPareja.getOrDefault(pareja.getId(), List.of())))
                .toList();

        List<CuentaJugador> jugadores = cuentas.stream().flatMap(c -> c.getJugadores().stream()).toList();
        BigDecimal esperado = sumar(jugadores.stream().map(CuentaJugador::getCosto).toList());
        BigDecimal cobrado = sumar(jugadores.stream().map(CuentaJugador::getPagado).toList());
        int conDeuda = (int) jugadores.stream().filter(j -> j.getSaldo().signum() > 0).count();

        return CuentaInscripcionesResponse.builder()
                .torneoId(torneoId)
                .totalEsperado(esperado)
                .totalCobrado(cobrado)
                .totalPendiente(sumar(jugadores.stream().map(CuentaJugador::getSaldo).toList()))
                .jugadoresConDeuda(conDeuda)
                .jugadoresAlDia(jugadores.size() - conDeuda)
                .parejas(cuentas)
                .build();
    }

    @Transactional
    public CuentaInscripcionesResponse registrar(Long torneoId, Long parejaId, PagoJugadorRequest request) {
        Pareja pareja = buscarPareja(torneoId, parejaId);
        Jugador jugador = integrante(pareja, request.getJugadorId());
        if (request.getFecha().isAfter(LocalDate.now())) {
            throw new EstadoInvalidoException("La fecha del pago no puede ser futura.");
        }
        BigDecimal costo = costo(pareja);
        if (costo.signum() == 0) {
            throw new EstadoInvalidoException("Este torneo no tiene costo de inscripción: no hay nada que cobrar.");
        }
        BigDecimal saldo = costo.subtract(pagado(pagoInscripcionRepository.findByParejaId(parejaId), jugador.getId()));
        if (request.getMonto().compareTo(saldo) > 0) {
            throw new EstadoInvalidoException(saldo.signum() <= 0
                    ? nombre(jugador) + " ya tiene la inscripción paga."
                    : nombre(jugador) + " debe " + Pesos.formatear(saldo) + ", no se le puede cobrar más que eso.");
        }

        pagoInscripcionRepository.save(PagoInscripcion.builder()
                .pareja(pareja)
                .jugador(jugador)
                .fecha(request.getFecha())
                .monto(request.getMonto())
                .medio(request.getMedio())
                .notas(request.getNotas())
                .registradoPor(UsuarioActual.nombre())
                .creadoEn(LocalDateTime.now())
                .build());
        log.info("[inscripciones] {} cobró ${} a {} ({}) en el torneo {}", UsuarioActual.nombre(), request.getMonto(),
                nombre(jugador), request.getMedio(), torneoId);
        return cuenta(torneoId);
    }

    @Transactional
    public CuentaInscripcionesResponse anular(Long torneoId, Long pagoId, String motivo) {
        PagoInscripcion pago = pagoInscripcionRepository.findById(pagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago de inscripción", pagoId));
        if (!pago.getPareja().getTorneo().getId().equals(torneoId)) {
            throw new ResourceNotFoundException("Pago de inscripción", pagoId);
        }
        if (pago.estaAnulado()) {
            throw new EstadoInvalidoException("Ese pago ya está anulado.");
        }
        pago.setAnuladoEn(LocalDateTime.now());
        pago.setAnuladoPor(UsuarioActual.nombre());
        pago.setMotivoAnulacion(motivo);
        pagoInscripcionRepository.save(pago);
        return cuenta(torneoId);
    }

    /**
     * La seña que el jugador pagó por Mercado Pago al anotarse, repartida entre los dos.
     * Se llama al aprobar la solicitud y también cuando el pago se acredita después de
     * aprobada; si ya se registró, no hace nada.
     */
    @Transactional
    public void registrarSeniaOnline(SolicitudInscripcion solicitud) {
        Pago pago = solicitud.getPago();
        Pareja pareja = solicitud.getPareja();
        if (pago == null || pareja == null || !solicitud.isPagada() || pago.getMontoSenia() == null) {
            return;
        }
        boolean yaRegistrada = pagoInscripcionRepository.findByParejaId(pareja.getId()).stream()
                .anyMatch(p -> p.getPagoOnline() != null && p.getPagoOnline().getId().equals(pago.getId()));
        if (yaRegistrada) {
            return;
        }
        BigDecimal mitad = pago.getMontoSenia().divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
        LocalDate fecha = pago.getPagadoEn() != null ? pago.getPagadoEn().toLocalDate() : LocalDate.now();
        List<PagoInscripcion> pagos = new ArrayList<>();
        pagos.add(seniaOnline(pareja, pareja.getJugador1(), pago, mitad, fecha));
        pagos.add(seniaOnline(pareja, pareja.getJugador2(), pago, pago.getMontoSenia().subtract(mitad), fecha));
        pagoInscripcionRepository.saveAll(pagos);
    }

    /** Que no se pueda borrar una pareja que ya pagó: la plata quedaría sin dueño. */
    @Transactional
    public void exigirSinPagos(Pareja pareja) {
        List<PagoInscripcion> pagos = pagoInscripcionRepository.findByParejaId(pareja.getId());
        if (pagos.stream().anyMatch(p -> !p.estaAnulado())) {
            throw new EstadoInvalidoException("Esta pareja ya pagó parte de la inscripción: anulá los pagos "
                    + "o retirala del torneo en vez de borrarla.");
        }
        pagoInscripcionRepository.deleteAll(pagos);
    }

    private static PagoInscripcion seniaOnline(Pareja pareja, Jugador jugador, Pago pago, BigDecimal monto, LocalDate fecha) {
        return PagoInscripcion.builder()
                .pareja(pareja)
                .jugador(jugador)
                .fecha(fecha)
                .monto(monto)
                .medio(MedioPago.MERCADO_PAGO)
                .notas("Seña online")
                .pagoOnline(pago)
                .registradoPor("sistema")
                .creadoEn(LocalDateTime.now())
                .build();
    }

    private CuentaPareja cuentaPareja(Pareja pareja, List<PagoInscripcion> pagos) {
        BigDecimal costo = costo(pareja);
        List<CuentaJugador> jugadores = new ArrayList<>();
        for (Jugador jugador : List.of(pareja.getJugador1(), pareja.getJugador2())) {
            BigDecimal pagado = pagado(pagos, jugador.getId());
            jugadores.add(CuentaJugador.builder()
                    .jugadorId(jugador.getId())
                    .nombre(nombre(jugador))
                    .telefono(jugador.getTelefono())
                    .costo(costo)
                    .pagado(pagado)
                    .saldo(costo.subtract(pagado).max(BigDecimal.ZERO))
                    .pagos(pagos.stream()
                            .filter(p -> p.getJugador().getId().equals(jugador.getId()))
                            .map(CobroInscripcionService::aPago)
                            .toList())
                    .build());
        }
        return CuentaPareja.builder()
                .parejaId(pareja.getId())
                .categoriaId(pareja.getCategoria() != null ? pareja.getCategoria().getId() : null)
                .categoriaNombre(pareja.getCategoria() != null ? pareja.getCategoria().getNombre() : null)
                .estado(pareja.getEstado())
                .saldo(sumar(jugadores.stream().map(CuentaJugador::getSaldo).toList()))
                .jugadores(jugadores)
                .build();
    }

    private static CuentaInscripcionesResponse.Pago aPago(PagoInscripcion pago) {
        return CuentaInscripcionesResponse.Pago.builder()
                .id(pago.getId())
                .fecha(pago.getFecha())
                .monto(pago.getMonto())
                .medio(pago.getMedio())
                .notas(pago.getNotas())
                .online(pago.getPagoOnline() != null)
                .registradoPor(pago.getRegistradoPor())
                .creadoEn(pago.getCreadoEn())
                .anulado(pago.estaAnulado())
                .anuladoPor(pago.getAnuladoPor())
                .motivoAnulacion(pago.getMotivoAnulacion())
                .build();
    }

    private static BigDecimal costo(Pareja pareja) {
        if (pareja.getCostoInscripcionJugador() != null) {
            return pareja.getCostoInscripcionJugador();
        }
        BigDecimal delTorneo = pareja.getTorneo() != null ? pareja.getTorneo().getCostoInscripcionJugador() : null;
        return delTorneo != null ? delTorneo : BigDecimal.ZERO;
    }

    private static BigDecimal pagado(List<PagoInscripcion> pagos, Long jugadorId) {
        return sumar(pagos.stream()
                .filter(p -> !p.estaAnulado() && p.getJugador().getId().equals(jugadorId))
                .map(PagoInscripcion::getMonto)
                .toList());
    }

    private static BigDecimal sumar(List<BigDecimal> montos) {
        return montos.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String nombre(Jugador jugador) {
        return (jugador.getNombre() + " " + jugador.getApellido()).trim();
    }

    private Jugador integrante(Pareja pareja, Long jugadorId) {
        if (pareja.getJugador1() != null && pareja.getJugador1().getId().equals(jugadorId)) {
            return pareja.getJugador1();
        }
        if (pareja.getJugador2() != null && pareja.getJugador2().getId().equals(jugadorId)) {
            return pareja.getJugador2();
        }
        throw new EstadoInvalidoException("Ese jugador no es de esta pareja.");
    }

    private Pareja buscarPareja(Long torneoId, Long parejaId) {
        Pareja pareja = parejaRepository.findById(parejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pareja", parejaId));
        if (!pareja.getTorneo().getId().equals(torneoId)) {
            throw new ResourceNotFoundException("Pareja", parejaId);
        }
        return pareja;
    }
}
