package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.response.EstadisticasResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.EstadoPago;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.SolicitudInscripcionRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstadisticaService - facturación y ausentes")
class EstadisticaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private TorneoRepository torneoRepository;
    @Mock
    private ParejaRepository parejaRepository;
    @Mock
    private SolicitudInscripcionRepository solicitudInscripcionRepository;
    @Mock
    private GastoRepository gastoRepository;

    @InjectMocks
    private EstadisticaService estadisticaService;

    private final Cancha cancha = Cancha.builder().id(1L).nombre("Cancha 1").build();

    private Reserva reserva(EstadoReserva estado, BigDecimal precio, Pago pago) {
        return Reserva.builder()
                .cancha(cancha)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(20, 0))
                .horaFin(LocalTime.of(21, 0))
                .precioAplicado(precio)
                .estado(estado)
                .pago(pago)
                .build();
    }

    private BigDecimal ingresoDelMesActual(EstadisticasResponse respuesta) {
        String mes = YearMonth.now().toString();
        return respuesta.getIngresosPorMes().stream()
                .filter(ingreso -> mes.equals(ingreso.getMes()))
                .map(EstadisticasResponse.IngresoMes::getTurnos)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private void conReservas(List<Reserva> reservas) {
        conReservasYGastos(reservas, List.of());
    }

    private void conReservasYGastos(List<Reserva> reservas, List<Gasto> gastos) {
        when(reservaRepository.findByFechaBetweenAndEstadoIn(any(), any(), anyList())).thenReturn(reservas);
        when(solicitudInscripcionRepository.findAll()).thenReturn(List.of());
        when(torneoRepository.findByActivoTrueAndEstadoIn(anyList())).thenReturn(List.of());
        when(gastoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(gastos);
    }

    private BigDecimal resultadoDelMesActual(EstadisticasResponse respuesta) {
        String mes = YearMonth.now().toString();
        return respuesta.getIngresosPorMes().stream()
                .filter(ingreso -> mes.equals(ingreso.getMes()))
                .map(EstadisticasResponse.IngresoMes::getResultado)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Un turno jugado factura el precio congelado completo")
    void turnoJugado_facturaTodo() {
        conReservas(List.of(reserva(EstadoReserva.FINALIZADA, new BigDecimal("20000.00"), null)));

        assertThat(ingresoDelMesActual(estadisticaService.obtener(null)))
                .isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("Si el cliente no vino, solo se factura la seña que quedó cobrada")
    void noShow_conSenia_facturaSoloLaSenia() {
        Pago pago = Pago.builder().estado(EstadoPago.APROBADO).porcentajeSenia(50).build();
        conReservas(List.of(reserva(EstadoReserva.NO_SHOW, new BigDecimal("20000.00"), pago)));

        // Contar el turno entero inflaría la facturación con plata que nunca entró.
        assertThat(ingresoDelMesActual(estadisticaService.obtener(null)))
                .isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("Un ausente sin seña online no factura nada")
    void noShow_sinPago_noFactura() {
        conReservas(List.of(reserva(EstadoReserva.NO_SHOW, new BigDecimal("20000.00"), null)));

        assertThat(ingresoDelMesActual(estadisticaService.obtener(null)))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("El ausente ocupa la cancha: cuenta en el mapa de ocupación")
    void noShow_cuentaComoCanchaOcupada() {
        conReservas(List.of(reserva(EstadoReserva.NO_SHOW, new BigDecimal("20000.00"), null)));

        EstadisticasResponse respuesta = estadisticaService.obtener(null);

        assertThat(respuesta.getCanchasMasUsadas()).hasSize(1);
        assertThat(respuesta.getCanchasMasUsadas().get(0).getReservas()).isEqualTo(1);
    }

    @Test
    @DisplayName("El resultado del mes descuenta los egresos, no solo suma lo facturado")
    void resultado_descuentaEgresos() {
        Gasto luz = Gasto.builder()
                .fecha(LocalDate.now())
                .categoria(CategoriaGasto.LUZ)
                .monto(new BigDecimal("8000.00"))
                .medio(MedioPago.TRANSFERENCIA)
                .build();
        conReservasYGastos(
                List.of(reserva(EstadoReserva.FINALIZADA, new BigDecimal("20000.00"), null)),
                List.of(luz));

        EstadisticasResponse respuesta = estadisticaService.obtener(null);

        assertThat(ingresoDelMesActual(respuesta)).isEqualByComparingTo("20000.00");
        assertThat(resultadoDelMesActual(respuesta)).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("Un gasto de otro mes no ensucia el resultado del mes actual")
    void resultado_gastoDeOtroMes_noAfecta() {
        Gasto viejo = Gasto.builder()
                .fecha(LocalDate.now().minusMonths(2))
                .categoria(CategoriaGasto.ALQUILER)
                .monto(new BigDecimal("50000.00"))
                .medio(MedioPago.TRANSFERENCIA)
                .build();
        conReservasYGastos(
                List.of(reserva(EstadoReserva.FINALIZADA, new BigDecimal("20000.00"), null)),
                List.of(viejo));

        assertThat(resultadoDelMesActual(estadisticaService.obtener(null))).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("La tasa de ausentes se mide sobre los turnos que debieron jugarse")
    void tasaNoShow_sobreTurnosQueDebieronJugarse() {
        conReservas(List.of(
                reserva(EstadoReserva.FINALIZADA, new BigDecimal("20000.00"), null),
                reserva(EstadoReserva.FINALIZADA, new BigDecimal("20000.00"), null),
                reserva(EstadoReserva.NO_SHOW, new BigDecimal("20000.00"), null),
                // Las canceladas no entran en el denominador: nadie tenía que venir.
                reserva(EstadoReserva.CANCELADA, new BigDecimal("20000.00"), null)));

        EstadisticasResponse respuesta = estadisticaService.obtener(null);

        assertThat(respuesta.getReservasNoShow()).isEqualTo(1);
        assertThat(respuesta.getTasaNoShow()).isEqualTo(1d / 3d);
    }
}
