package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.CierreCajaRequest;
import com.padel.rankpadel.dto.response.CierreCajaResponse;
import com.padel.rankpadel.dto.response.CierreCajaResponse.TotalMedio;
import com.padel.rankpadel.dto.response.CobroResponse;
import com.padel.rankpadel.dto.response.MovimientoCajaResponse;
import com.padel.rankpadel.entity.CierreCaja;
import com.padel.rankpadel.entity.Cobro;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.Venta;
import com.padel.rankpadel.enums.EstadoPago;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CierreCajaRepository;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.repository.CobroRepository.TotalPorReserva;
import com.padel.rankpadel.repository.PagoRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.VentaRepository;
import com.padel.rankpadel.util.MontosReserva;

import lombok.RequiredArgsConstructor;

/**
 * Cierre de caja del día: cuánta plata entró, por qué medio, y cuánto queda por cobrar.
 */
@Service
@RequiredArgsConstructor
public class CajaService {

    /** Turnos que generan saldo a cobrar. Un cancelado no debe nada. */
    private static final Set<EstadoReserva> CON_SALDO = Set.of(
            EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA);

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Logger log = LoggerFactory.getLogger(CajaService.class);

    private final CobroRepository cobroRepository;
    private final PagoRepository pagoRepository;
    private final ReservaRepository reservaRepository;
    private final GastoRepository gastoRepository;
    private final VentaRepository ventaRepository;
    private final CierreCajaRepository cierreCajaRepository;
    private final CobroService cobroService;
    private final VentaService ventaService;
    private final GastoService gastoService;

    @Transactional(readOnly = true)
    public CierreCajaResponse cierre(LocalDate fecha) {
        LocalDateTime desde = fecha.atStartOfDay();
        LocalDateTime hasta = desde.plusDays(1);

        List<Cobro> cobros = cobroRepository.findDelDia(desde, hasta);
        List<Venta> ventas = ventaRepository.findDelDiaConItems(desde, hasta);

        // Los turnos y lo que se vendió en el mostrador van al mismo arqueo: la plata del
        // cajón no distingue si entró por una cancha o por un tubo de pelotas.
        Map<MedioPago, BigDecimal> totales = new HashMap<>();
        Map<MedioPago, Long> cantidades = new HashMap<>();
        for (Cobro cobro : cobros) {
            MedioPago medio = cobro.getMedio() != null ? cobro.getMedio() : MedioPago.OTRO;
            totales.merge(medio, cobro.getMonto(), BigDecimal::add);
            cantidades.merge(medio, 1L, Long::sum);
        }
        for (Venta venta : ventas) {
            // Una venta sin medio está anotada en la cuenta de un turno: todavía no es
            // plata que entró. Entra al arqueo recién cuando se cobra el turno, y ese
            // cobro ya está contado más arriba. Sumarla acá la contaría dos veces.
            if (venta.getMedio() == null) {
                continue;
            }
            totales.merge(venta.getMedio(), venta.getTotal(), BigDecimal::add);
            cantidades.merge(venta.getMedio(), 1L, Long::sum);
        }

        List<TotalMedio> porMedio = totales.entrySet().stream()
                .map(entrada -> TotalMedio.builder()
                        .medio(entrada.getKey().name())
                        .cantidad(cantidades.getOrDefault(entrada.getKey(), 0L))
                        .total(entrada.getValue())
                        .build())
                .sorted((a, b) -> b.getTotal().compareTo(a.getTotal()))
                .toList();

        BigDecimal totalMostrador = totales.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Lo que salió del cajón también cuenta para el arqueo: si se pagó al gasista en
        // efectivo, esa plata ya no está aunque nadie la haya cobrado de menos.
        BigDecimal egresos = gastoRepository.totalDelDia(fecha);
        BigDecimal egresosEfectivo = gastoRepository.totalDelDiaPorMedio(fecha, MedioPago.EFECTIVO);
        BigDecimal efectivoEsperado = totales.getOrDefault(MedioPago.EFECTIVO, BigDecimal.ZERO)
                .subtract(egresosEfectivo);

        // Las señas de Mercado Pago se acreditan en la cuenta, no en el cajón: van
        // separadas para que el arqueo de efectivo cierre.
        BigDecimal seniasOnline = pagoRepository
                .findByEstadoAndPagadoEnBetween(EstadoPago.APROBADO, desde, hasta).stream()
                .map(Pago::getMontoSenia)
                .filter(monto -> monto != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SaldoDelDia saldo = calcularSaldoPendiente(fecha);

        return CierreCajaResponse.builder()
                .fecha(fecha)
                .porMedio(porMedio)
                .efectivoEsperado(efectivoEsperado)
                .totalMostrador(totalMostrador)
                .seniasOnline(seniasOnline)
                .totalDelDia(totalMostrador.add(seniasOnline))
                .turnosConSaldo(saldo.turnos())
                .saldoPendiente(saldo.monto())
                .movimientos(agruparEnMovimientos(cobros))
                .egresos(egresos)
                .egresosEfectivo(egresosEfectivo)
                .resultado(totalMostrador.add(seniasOnline).subtract(egresos))
                .gastos(gastoService.listarDelDia(fecha))
                .ventas(ventas.stream().map(ventaService::aResponse).toList())
                .totalVentas(ventas.stream().map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .arqueo(cierreCajaRepository.findByFecha(fecha).map(this::aArqueo).orElse(null))
                .cobrosAnulados(cobroRepository.findAnuladosDelDia(desde, hasta).stream()
                        .map(cobroService::aResponse).toList())
                .ventasAnuladas(ventaRepository.findAnuladasDelDia(desde, hasta).stream()
                        .map(ventaService::aResponse).toList())
                .build();
    }

    /**
     * Firma el arqueo del día. Los totales se guardan congelados: si mañana se corrige un
     * turno viejo, lo que se contó ayer no puede moverse, porque contra ese número se
     * contó la plata. A partir de acá los movimientos de esa fecha quedan bloqueados.
     */
    @Transactional
    public CierreCajaResponse cerrar(CierreCajaRequest request) {
        LocalDate fecha = request.getFecha();
        if (cierreCajaRepository.existsByFecha(fecha)) {
            throw new EstadoInvalidoException("La caja del " + fecha.format(DIA) + " ya está cerrada");
        }
        if (fecha.isAfter(LocalDate.now())) {
            throw new EstadoInvalidoException("No se puede cerrar un día que todavía no pasó");
        }

        CierreCajaResponse actual = cierre(fecha);
        BigDecimal contado = request.getEfectivoContado();

        CierreCaja cierre = cierreCajaRepository.save(CierreCaja.builder()
                .fecha(fecha)
                .efectivoEsperado(actual.getEfectivoEsperado())
                .efectivoContado(contado)
                .diferencia(contado.subtract(actual.getEfectivoEsperado()))
                .totalMostrador(actual.getTotalMostrador())
                .seniasOnline(actual.getSeniasOnline())
                .egresos(actual.getEgresos())
                .cerradoPor(usuarioActual())
                .cerradoEn(LocalDateTime.now())
                .notas(request.getNotas())
                .build());

        log.info("[caja] {} cerró el {}: esperaba ${}, contó ${}, diferencia ${}",
                cierre.getCerradoPor(), fecha, cierre.getEfectivoEsperado(),
                cierre.getEfectivoContado(), cierre.getDiferencia());

        actual.setArqueo(aArqueo(cierre));
        return actual;
    }

    /**
     * Reabre un día ya cerrado. Existe porque el error se descubre después: alguien cargó
     * un cobro mal y recién se dio cuenta al otro día. Queda en el log quién lo reabrió.
     */
    @Transactional
    public CierreCajaResponse reabrir(LocalDate fecha) {
        CierreCaja cierre = cierreCajaRepository.findByFecha(fecha)
                .orElseThrow(() -> new EstadoInvalidoException(
                        "La caja del " + fecha.format(DIA) + " no está cerrada"));
        log.warn("[caja] {} reabrió el cierre del {} (lo había cerrado {} con diferencia ${})",
                usuarioActual(), fecha, cierre.getCerradoPor(), cierre.getDiferencia());
        cierreCajaRepository.delete(cierre);
        return cierre(fecha);
    }

    private CierreCajaResponse.Arqueo aArqueo(CierreCaja cierre) {
        return CierreCajaResponse.Arqueo.builder()
                .efectivoContado(cierre.getEfectivoContado())
                .diferencia(cierre.getDiferencia())
                .cerradoPor(cierre.getCerradoPor())
                .cerradoEn(cierre.getCerradoEn())
                .notas(cierre.getNotas())
                .build();
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    @Transactional(readOnly = true)
    public List<CobroResponse> movimientosDe(LocalDate fecha) {
        return cobroRepository.findDelDia(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()).stream()
                .map(cobroService::aResponse)
                .toList();
    }

    /**
     * Junta en una línea los cobros que para el cliente fueron un solo pago: mismo turno
     * (cliente, cancha y día), mismo medio y cobrados en el mismo momento. Antes un turno
     * de dos horas aparecía en la caja como dos pagos de la mitad cada uno.
     */
    private List<MovimientoCajaResponse> agruparEnMovimientos(List<Cobro> cobros) {
        Map<String, List<Cobro>> porTurno = new LinkedHashMap<>();
        for (Cobro cobro : cobros) {
            porTurno.computeIfAbsent(claveMovimiento(cobro), clave -> new ArrayList<>()).add(cobro);
        }
        return porTurno.values().stream().map(this::aMovimiento).toList();
    }

    /**
     * Los cobros de un mismo turno se registran uno atrás del otro, así que se agrupan por
     * minuto: dos pagos parciales del mismo turno hechos en momentos distintos —una seña a
     * la mañana y el resto a la noche— tienen que seguir viéndose separados.
     */
    private String claveMovimiento(Cobro cobro) {
        Reserva reserva = cobro.getReserva();
        return String.join("|",
                reserva != null && reserva.getCancha() != null
                        ? String.valueOf(reserva.getCancha().getId()) : "-",
                reserva != null ? String.valueOf(reserva.getFecha()) : "-",
                reserva != null ? String.valueOf(reserva.getClienteTelefono()) : "-",
                cobro.getMedio() != null ? cobro.getMedio().name() : "-",
                cobro.getCobradoEn().truncatedTo(ChronoUnit.MINUTES).toString());
    }

    private MovimientoCajaResponse aMovimiento(List<Cobro> delTurno) {
        Cobro primero = delTurno.get(0);
        Reserva reserva = primero.getReserva();
        List<Reserva> reservas = delTurno.stream()
                .map(Cobro::getReserva)
                .filter(r -> r != null && r.getHoraInicio() != null)
                .toList();

        return MovimientoCajaResponse.builder()
                .cobroIds(delTurno.stream().map(Cobro::getId).toList())
                .clienteNombre(reserva != null ? reserva.getClienteNombre() : null)
                .canchaNombre(reserva != null && reserva.getCancha() != null
                        ? reserva.getCancha().getNombre() : null)
                .fechaTurno(reserva != null ? reserva.getFecha() : null)
                .horaInicio(reservas.stream().map(Reserva::getHoraInicio)
                        .min(Comparator.naturalOrder()).orElse(null))
                // El fin es el del último horario del lote, no el mayor de los horaFin: un
                // turno que termina a las 00:00 tiene el horaFin más chico de todos.
                .horaFin(reservas.stream()
                        .max(Comparator.comparing(Reserva::getHoraInicio))
                        .map(Reserva::getHoraFin).orElse(null))
                .monto(delTurno.stream().map(Cobro::getMonto)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .medio(primero.getMedio() != null ? primero.getMedio().name() : null)
                .cobradoEn(primero.getCobradoEn())
                .registradoPor(primero.getRegistradoPor())
                .notas(delTurno.stream().map(Cobro::getNotas)
                        .filter(nota -> nota != null && !nota.isBlank())
                        .findFirst().orElse(null))
                .build();
    }

    private SaldoDelDia calcularSaldoPendiente(LocalDate fecha) {
        List<Reserva> reservas = reservaRepository.findByFechaAndEstadoIn(fecha, CON_SALDO);
        if (reservas.isEmpty()) {
            return new SaldoDelDia(0, BigDecimal.ZERO);
        }

        // Dos consultas agrupadas para todos los turnos del día, no una por fila.
        List<Long> ids = reservas.stream().map(Reserva::getId).toList();
        Map<Long, BigDecimal> cobrado = new HashMap<>();
        for (TotalPorReserva total : cobroRepository.totalesPorReserva(ids)) {
            cobrado.put(total.getReservaId(), total.getTotal());
        }
        // Lo consumido y no pagado también es plata que el club tiene que cobrar hoy.
        Map<Long, BigDecimal> consumo = new HashMap<>();
        for (VentaRepository.ConsumoPorReserva total : ventaRepository.consumoACuentaDe(ids)) {
            consumo.put(total.getReservaId(), total.getTotal());
        }

        BigDecimal monto = BigDecimal.ZERO;
        long turnos = 0;
        for (Reserva reserva : reservas) {
            BigDecimal pendiente = MontosReserva.saldo(
                    reserva, cobrado.get(reserva.getId()), consumo.get(reserva.getId()));
            if (pendiente.compareTo(BigDecimal.ZERO) > 0) {
                monto = monto.add(pendiente);
                turnos++;
            }
        }
        return new SaldoDelDia(turnos, monto);
    }

    private record SaldoDelDia(long turnos, BigDecimal monto) {
    }
}
