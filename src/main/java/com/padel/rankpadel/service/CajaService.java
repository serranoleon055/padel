package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.CierreCajaResponse;
import com.padel.rankpadel.dto.response.CierreCajaResponse.TotalMedio;
import com.padel.rankpadel.dto.response.CobroResponse;
import com.padel.rankpadel.entity.Cobro;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoPago;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.repository.CobroRepository.TotalPorReserva;
import com.padel.rankpadel.repository.PagoRepository;
import com.padel.rankpadel.repository.ReservaRepository;
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

    private final CobroRepository cobroRepository;
    private final PagoRepository pagoRepository;
    private final ReservaRepository reservaRepository;
    private final GastoRepository gastoRepository;
    private final CobroService cobroService;
    private final GastoService gastoService;

    @Transactional(readOnly = true)
    public CierreCajaResponse cierre(LocalDate fecha) {
        LocalDateTime desde = fecha.atStartOfDay();
        LocalDateTime hasta = desde.plusDays(1);

        List<Cobro> cobros = cobroRepository.findDelDia(desde, hasta);

        Map<MedioPago, BigDecimal> totales = new HashMap<>();
        Map<MedioPago, Long> cantidades = new HashMap<>();
        for (Cobro cobro : cobros) {
            MedioPago medio = cobro.getMedio() != null ? cobro.getMedio() : MedioPago.OTRO;
            totales.merge(medio, cobro.getMonto(), BigDecimal::add);
            cantidades.merge(medio, 1L, Long::sum);
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
                .movimientos(cobros.stream().map(cobroService::aResponse).toList())
                .egresos(egresos)
                .egresosEfectivo(egresosEfectivo)
                .resultado(totalMostrador.add(seniasOnline).subtract(egresos))
                .gastos(gastoService.listarDelDia(fecha))
                .build();
    }

    @Transactional(readOnly = true)
    public List<CobroResponse> movimientosDe(LocalDate fecha) {
        return cobroRepository.findDelDia(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()).stream()
                .map(cobroService::aResponse)
                .toList();
    }

    private SaldoDelDia calcularSaldoPendiente(LocalDate fecha) {
        List<Reserva> reservas = reservaRepository.findByFechaAndEstadoIn(fecha, CON_SALDO);
        if (reservas.isEmpty()) {
            return new SaldoDelDia(0, BigDecimal.ZERO);
        }

        // Una sola consulta para todos los turnos del día en vez de una por fila.
        Map<Long, BigDecimal> cobrado = new HashMap<>();
        for (TotalPorReserva total : cobroRepository.totalesPorReserva(
                reservas.stream().map(Reserva::getId).toList())) {
            cobrado.put(total.getReservaId(), total.getTotal());
        }

        BigDecimal monto = BigDecimal.ZERO;
        long turnos = 0;
        for (Reserva reserva : reservas) {
            BigDecimal pendiente = MontosReserva.saldo(reserva, cobrado.get(reserva.getId()));
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
