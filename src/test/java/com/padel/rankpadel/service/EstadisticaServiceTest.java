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
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.entity.HorarioCancha;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.EstadoPago;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.repository.HorarioCanchaRepository;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.SolicitudInscripcionRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.repository.VentaRepository;

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
    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private CanchaRepository canchaRepository;
    @Mock
    private HorarioCanchaRepository horarioCanchaRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private CobroRepository cobroRepository;

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
        when(reservaRepository.findParaEstadisticas(any(), any())).thenReturn(reservas);
        when(solicitudInscripcionRepository.findAll()).thenReturn(List.of());
        when(torneoRepository.findByActivoTrueAndEstadoIn(anyList())).thenReturn(List.of());
        when(gastoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(gastos);
        when(ventaRepository.totalPorMes(any())).thenReturn(List.of());
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
    @DisplayName("El cupo del embudo se desglosa por categoría, no contra el total del torneo")
    void embudo_cupoPorCategoria() {
        Categoria septima = Categoria.builder().id(10L).nombre("Séptima").build();
        Categoria sexta = Categoria.builder().id(20L).nombre("Sexta").build();
        Torneo torneo = Torneo.builder()
                .id(1L)
                .nombre("Apertura")
                .categorias(List.of(septima, sexta))
                .cuposPorCategoria(new java.util.HashMap<>(java.util.Map.of(10L, 12, 20L, 8)))
                .build();

        when(reservaRepository.findParaEstadisticas(any(), any())).thenReturn(List.of());
        when(solicitudInscripcionRepository.findAll()).thenReturn(List.of());
        when(gastoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        when(ventaRepository.totalPorMes(any())).thenReturn(List.of());
        when(torneoRepository.findByActivoTrueAndEstadoIn(anyList())).thenReturn(List.of(torneo));
        when(parejaRepository.contarPorTorneoYCategoria(anyList())).thenReturn(List.of(
                new Object[] { 1L, 10L, "Séptima", 12L },
                new Object[] { 1L, 20L, "Sexta", 5L }));

        EstadisticasResponse.EmbudoTorneo embudo = estadisticaService.obtener(null).getEmbudoTorneos().get(0);

        // Antes se mostraba "17 / 12": las parejas de todo el torneo contra el cupo suelto.
        assertThat(embudo.getInscriptos()).isEqualTo(17);
        assertThat(embudo.getCupo()).isEqualTo(20);
        assertThat(embudo.getCategorias())
                .extracting(EstadisticasResponse.CupoCategoria::getCategoriaNombre,
                        EstadisticasResponse.CupoCategoria::getInscriptos,
                        EstadisticasResponse.CupoCategoria::getCupo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Sexta", 5L, 8),
                        org.assertj.core.groups.Tuple.tuple("Séptima", 12L, 12));
    }

    @Test
    @DisplayName("Si a una categoría le falta el cupo, el total del torneo no se inventa")
    void embudo_sinCupoEnUnaCategoria_totalNulo() {
        Categoria septima = Categoria.builder().id(10L).nombre("Séptima").build();
        Categoria sexta = Categoria.builder().id(20L).nombre("Sexta").build();
        Torneo torneo = Torneo.builder()
                .id(1L)
                .nombre("Apertura")
                .categorias(List.of(septima, sexta))
                .cuposPorCategoria(new java.util.HashMap<>(java.util.Map.of(10L, 12)))
                .build();

        when(reservaRepository.findParaEstadisticas(any(), any())).thenReturn(List.of());
        when(solicitudInscripcionRepository.findAll()).thenReturn(List.of());
        when(gastoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        when(ventaRepository.totalPorMes(any())).thenReturn(List.of());
        when(torneoRepository.findByActivoTrueAndEstadoIn(anyList())).thenReturn(List.of(torneo));
        when(parejaRepository.contarPorTorneoYCategoria(anyList())).thenReturn(List.<Object[]>of(
                new Object[] { 1L, 10L, "Séptima", 3L }));

        EstadisticasResponse.EmbudoTorneo embudo = estadisticaService.obtener(null).getEmbudoTorneos().get(0);

        assertThat(embudo.getCupo()).isNull();
        assertThat(embudo.getCategorias()).extracting(EstadisticasResponse.CupoCategoria::getCupo)
                .containsExactly(null, 12);
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
    @DisplayName("La ocupación se mide sobre las horas que el club estuvo abierto, no sobre el día entero")
    void ocupacion_sobreHorasAbiertas() {
        // Una cancha abierta de 18 a 23 (5 h/día) con un turno de 2 h vendido hoy.
        HorarioCancha horario = HorarioCancha.builder()
                .horaApertura(LocalTime.of(18, 0)).horaCierre(LocalTime.of(23, 0))
                .diasActivos(null).activo(true).build();
        Reserva turno = reserva(EstadoReserva.FINALIZADA, new BigDecimal("20000.00"), null);
        turno.setDuracionMin(120);

        conReservas(List.of(turno));
        when(canchaRepository.findByActivoTrue()).thenReturn(List.of(cancha));
        when(horarioCanchaRepository.findByCanchaIdAndActivoTrue(1L)).thenReturn(List.of(horario));
        when(ventaRepository.rankingProductos(any(), any())).thenReturn(List.of());
        when(productoRepository.buscar(null, true)).thenReturn(List.of());
        when(productoRepository.conStockBajo()).thenReturn(List.of());

        EstadisticasResponse.OcupacionCancha ocupacion = estadisticaService.obtener(null)
                .getOcupacionPorCancha().get(0);

        assertThat(ocupacion.getHorasVendidas()).isEqualTo(2);
        // Con el día entero como denominador daría 8%: sería un número inútil.
        assertThat(ocupacion.getHorasDisponibles()).isGreaterThan(0);
        assertThat(ocupacion.getOcupacion()).isGreaterThan(0d).isLessThanOrEqualTo(1d);
    }

    @Test
    @DisplayName("La variación contra el mes anterior queda nula si el mes anterior fue cero")
    void resumen_sinMesAnterior_variacionNula() {
        conReservas(List.of(reserva(EstadoReserva.FINALIZADA, new BigDecimal("20000.00"), null)));
        when(canchaRepository.findByActivoTrue()).thenReturn(List.of());
        when(ventaRepository.rankingProductos(any(), any())).thenReturn(List.of());
        when(productoRepository.buscar(null, true)).thenReturn(List.of());
        when(productoRepository.conStockBajo()).thenReturn(List.of());

        EstadisticasResponse.ResumenMes resumen = estadisticaService.obtener(null).getMesActual();

        // Dividir por cero daría un "+∞%" que no dice nada.
        assertThat(resumen.getVariacion()).isNull();
        assertThat(resumen.getFacturado()).isEqualByComparingTo("20000.00");
        assertThat(resumen.getTurnosJugados()).isEqualTo(1);
        assertThat(resumen.getTicketPromedio()).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("Lo vendido en el mostrador suma al resultado del mes")
    void resultado_sumaLasVentasDelMostrador() {
        when(reservaRepository.findParaEstadisticas(any(), any()))
                .thenReturn(List.of(reserva(EstadoReserva.FINALIZADA, new BigDecimal("20000.00"), null)));
        when(solicitudInscripcionRepository.findAll()).thenReturn(List.of());
        when(torneoRepository.findByActivoTrueAndEstadoIn(anyList())).thenReturn(List.of());
        when(gastoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        when(ventaRepository.totalPorMes(any()))
                .thenReturn(List.of(ventasDelMes(YearMonth.now().toString(), "35000.00")));

        EstadisticasResponse respuesta = estadisticaService.obtener(null);

        // 20.000 de cancha + 35.000 de mostrador, sin gastos.
        assertThat(resultadoDelMesActual(respuesta)).isEqualByComparingTo("55000.00");
    }

    /** El mes viene como "2026-08", el mismo formato que devuelve la consulta agrupada. */
    private VentaRepository.TotalPorMes ventasDelMes(String mes, String total) {
        return new VentaRepository.TotalPorMes() {
            @Override
            public String getMes() {
                return mes;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
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
