package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.padel.rankpadel.dto.request.CierreCajaRequest;
import com.padel.rankpadel.dto.response.CierreCajaResponse;
import com.padel.rankpadel.dto.response.GastoResponse;
import com.padel.rankpadel.dto.response.MovimientoSueltoResponse;
import com.padel.rankpadel.entity.CierreCaja;
import com.padel.rankpadel.entity.Cobro;
import com.padel.rankpadel.entity.MovimientoCaja;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Venta;
import com.padel.rankpadel.enums.ConceptoMovimientoCaja;
import com.padel.rankpadel.enums.ConceptoPago;
import com.padel.rankpadel.enums.EstadoPago;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoMovimientoCaja;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CierreCajaRepository;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.repository.MovimientoCajaRepository;
import com.padel.rankpadel.repository.PagoProveedorRepository;
import com.padel.rankpadel.repository.PagoRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.VentaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CajaService - el arqueo de la jornada")
class CajaServiceTest {

    @Mock
    private CobroRepository cobroRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private GastoRepository gastoRepository;
    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private CierreCajaRepository cierreCajaRepository;
    @Mock
    private CobroService cobroService;
    @Mock
    private VentaService ventaService;
    @Mock
    private GastoService gastoService;
    @Mock
    private MovimientoCajaRepository movimientoCajaRepository;
    @Mock
    private PagoProveedorRepository pagoProveedorRepository;
    @Mock
    private MovimientoCajaService movimientoCajaService;
    @Mock
    private DisponibilidadCanchaService disponibilidadCanchaService;

    @InjectMocks
    private CajaService cajaService;

    /** La noche del sábado. Cierra a las 2 del domingo, así que la jornada cruza el día. */
    private static final LocalDate JORNADA = LocalDate.of(2026, 8, 15);

    @BeforeEach
    void setUp() {
        lenient().when(gastoRepository.totalDeLaJornada(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(gastoRepository.totalDeLaJornadaPorMedio(any(), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(pagoRepository.findByEstadoAndJornada(any(), any())).thenReturn(List.of());
        lenient().when(cobroRepository.findDeLaJornada(any())).thenReturn(List.of());
        lenient().when(cobroRepository.findAnuladosDeLaJornada(any())).thenReturn(List.of());
        lenient().when(ventaRepository.findDeLaJornadaConItems(any())).thenReturn(List.of());
        lenient().when(ventaRepository.findAnuladasDeLaJornada(any())).thenReturn(List.of());
        lenient().when(reservaRepository.findByFechaAndEstadoIn(any(), any())).thenReturn(List.of());
        lenient().when(gastoService.listarDeLaJornada(any())).thenReturn(List.<GastoResponse>of());
        lenient().when(gastoService.listarAnuladosDeLaJornada(any())).thenReturn(List.<GastoResponse>of());
        lenient().when(cierreCajaRepository.findByFechaAndAnuladoEnIsNull(any())).thenReturn(Optional.empty());
        lenient().when(movimientoCajaRepository.findDeLaJornada(any())).thenReturn(List.of());
        lenient().when(pagoProveedorRepository.totalDeLaJornada(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(pagoProveedorRepository.totalDeLaJornadaPorMedio(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(movimientoCajaService.listarAnuladosDeLaJornada(any()))
                .thenReturn(List.<MovimientoSueltoResponse>of());
        lenient().when(movimientoCajaService.estaAbierta(any())).thenReturn(false);
        lenient().when(disponibilidadCanchaService.fechaDeJornadaActual()).thenReturn(JORNADA);
        entrarComo("dueño", "ROLE_ADMIN", "ROLE_DUENIO");
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void entrarComo(String usuario, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null,
                        java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }

    private Cobro cobro(String monto, MedioPago medio) {
        return Cobro.builder()
                .id(1L)
                .monto(new BigDecimal(monto))
                .medio(medio)
                .jornada(JORNADA)
                .cobradoEn(LocalDateTime.of(2026, 8, 15, 21, 0))
                .build();
    }

    private Venta venta(String total, MedioPago medio) {
        return Venta.builder()
                .id(1L)
                .total(new BigDecimal(total))
                .medio(medio)
                .jornada(JORNADA)
                .fecha(LocalDateTime.of(2026, 8, 15, 21, 30))
                .items(List.of())
                .build();
    }

    private Pago pago(String senia, ConceptoPago concepto) {
        return Pago.builder()
                .id(1L)
                .estado(EstadoPago.APROBADO)
                .concepto(concepto)
                .montoSenia(new BigDecimal(senia))
                .jornada(JORNADA)
                .build();
    }

    @Nested
    @DisplayName("Efectivo esperado")
    class EfectivoEsperado {

        @Test
        @DisplayName("Resta los egresos de la JORNADA, no los del día de calendario")
        void restaLosEgresosDeLaJornada() {
            // Es el bug que arregló V58: un pago al gasista en efectivo a las 00:30 es de
            // esta jornada, pero se cargaba con fecha de "hoy" y no se descontaba. Faltaba
            // plata esta noche y sobraba la siguiente, todas las noches.
            when(cobroRepository.findDeLaJornada(JORNADA))
                    .thenReturn(List.of(cobro("20000", MedioPago.EFECTIVO)));
            when(gastoRepository.totalDeLaJornadaPorMedio(JORNADA, MedioPago.EFECTIVO))
                    .thenReturn(new BigDecimal("5000"));
            when(gastoRepository.totalDeLaJornada(JORNADA)).thenReturn(new BigDecimal("5000"));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("15000");
            assertThat(cierre.getEgresosEfectivo()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("Lo cobrado por transferencia no tiene que estar en el cajón")
        void soloElEfectivoVaAlCajon() {
            when(cobroRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    cobro("20000", MedioPago.EFECTIVO),
                    cobro("30000", MedioPago.TRANSFERENCIA)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("20000");
            assertThat(cierre.getTotalMostrador()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("El consumo anotado a la cuenta de un turno no se cuenta dos veces")
        void ventaSinMedio_noSeCuentaDosVeces() {
            // Una venta sin medio está anotada en la cuenta del turno: todavía no es plata
            // que entró. Entra cuando se cobra el turno, y ese cobro ya está contado.
            when(cobroRepository.findDeLaJornada(JORNADA))
                    .thenReturn(List.of(cobro("20000", MedioPago.EFECTIVO)));
            when(ventaRepository.findDeLaJornadaConItems(JORNADA))
                    .thenReturn(List.of(venta("8000", null)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getTotalMostrador()).isEqualByComparingTo("20000");
            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("20000");
            // La venta sí aparece en el detalle: el club tiene que ver qué consumieron.
            assertThat(cierre.getTotalVentas()).isEqualByComparingTo("8000");
        }
    }

    @Nested
    @DisplayName("Señas online")
    class SeniasOnline {

        @Test
        @DisplayName("Las inscripciones a torneos no se cuentan como seña de cancha")
        void inscripciones_vanAparte() {
            // Las dos entran por Mercado Pago, pero el campo se rotula "señas": el club
            // veía como seña de turno la plata de un torneo.
            when(pagoRepository.findByEstadoAndJornada(EstadoPago.APROBADO, JORNADA)).thenReturn(List.of(
                    pago("10000", ConceptoPago.RESERVA),
                    pago("25000", ConceptoPago.INSCRIPCION)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getSeniasOnline()).isEqualByComparingTo("10000");
            assertThat(cierre.getInscripcionesOnline()).isEqualByComparingTo("25000");
            // Las dos son plata del día, así que el total del día sigue sumándolas.
            assertThat(cierre.getTotalDelDia()).isEqualByComparingTo("35000");
        }

        @Test
        @DisplayName("Las señas de Mercado Pago no van al cajón")
        void seniasNoVanAlEfectivo() {
            when(pagoRepository.findByEstadoAndJornada(EstadoPago.APROBADO, JORNADA))
                    .thenReturn(List.of(pago("10000", ConceptoPago.RESERVA)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Firmar y reabrir")
    class FirmarYReabrir {

        private CierreCajaRequest pedidoDeCierre(String contado) {
            CierreCajaRequest request = new CierreCajaRequest();
            request.setFecha(JORNADA);
            request.setEfectivoContado(new BigDecimal(contado));
            return request;
        }

        @Test
        @DisplayName("Los totales quedan congelados y la diferencia es contado menos esperado")
        void cerrar_congelaLosTotales() {
            when(cobroRepository.findDeLaJornada(JORNADA))
                    .thenReturn(List.of(cobro("20000", MedioPago.EFECTIVO)));
            when(cierreCajaRepository.existsByFechaAndAnuladoEnIsNull(JORNADA)).thenReturn(false);
            when(cierreCajaRepository.save(any(CierreCaja.class))).thenAnswer(i -> i.getArgument(0));

            cajaService.cerrar(pedidoDeCierre("18000"));

            ArgumentCaptor<CierreCaja> firmado = ArgumentCaptor.forClass(CierreCaja.class);
            verify(cierreCajaRepository).save(firmado.capture());
            assertThat(firmado.getValue().getEfectivoEsperado()).isEqualByComparingTo("20000");
            assertThat(firmado.getValue().getEfectivoContado()).isEqualByComparingTo("18000");
            // Negativo: faltaron 2000 en el cajón.
            assertThat(firmado.getValue().getDiferencia()).isEqualByComparingTo("-2000");
            assertThat(firmado.getValue().getCerradoPor()).isEqualTo("dueño");
        }

        @Test
        @DisplayName("Una jornada ya arqueada no se cierra dos veces")
        void cerrar_dosVeces_rechaza() {
            when(cierreCajaRepository.existsByFechaAndAnuladoEnIsNull(JORNADA)).thenReturn(true);

            assertThatThrownBy(() -> cajaService.cerrar(pedidoDeCierre("18000")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya está cerrada");
        }

        @Test
        @DisplayName("No se arquea un día que todavía no pasó, medido contra la jornada")
        void cerrar_diaFuturo_rechaza() {
            // Contra la jornada y no contra el calendario: a las 00:30 el club está
            // cerrando la noche de ayer, y esa jornada sí se puede arquear.
            CierreCajaRequest request = new CierreCajaRequest();
            request.setFecha(JORNADA.plusDays(1));
            request.setEfectivoContado(new BigDecimal("18000"));
            when(cierreCajaRepository.existsByFechaAndAnuladoEnIsNull(JORNADA.plusDays(1)))
                    .thenReturn(false);

            assertThatThrownBy(() -> cajaService.cerrar(request))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("todavía no pasó");
        }

        @Test
        @DisplayName("Reabrir no borra el arqueo firmado: lo deja anulado")
        void reabrir_esBajaLogica() {
            CierreCaja firmado = CierreCaja.builder()
                    .id(3L)
                    .fecha(JORNADA)
                    .efectivoEsperado(new BigDecimal("20000"))
                    .efectivoContado(new BigDecimal("18000"))
                    .diferencia(new BigDecimal("-2000"))
                    .cerradoPor("empleado")
                    .cerradoEn(LocalDateTime.of(2026, 8, 16, 2, 15))
                    .build();
            when(cierreCajaRepository.findByFechaAndAnuladoEnIsNull(JORNADA))
                    .thenReturn(Optional.of(firmado), Optional.empty());

            cajaService.reabrir(JORNADA, "había un cobro mal cargado");

            assertThat(firmado.getAnuladoEn()).isNotNull();
            assertThat(firmado.getAnuladoPor()).isEqualTo("dueño");
            assertThat(firmado.getMotivoReapertura()).isEqualTo("había un cobro mal cargado");
            assertThat(firmado.estaVigente()).isFalse();
            // El faltante de 2000 que firmó el empleado tiene que quedar en la base.
            assertThat(firmado.getDiferencia()).isEqualByComparingTo("-2000");
            verify(cierreCajaRepository, never()).delete(any());
            verify(cierreCajaRepository).save(firmado);
        }

        @Test
        @DisplayName("No se reabre una jornada que no está cerrada")
        void reabrir_sinCierre_rechaza() {
            when(cierreCajaRepository.findByFechaAndAnuladoEnIsNull(JORNADA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cajaService.reabrir(JORNADA, "sin motivo"))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("no está cerrada");
        }
    }

    @Nested
    @DisplayName("Qué ve cada rol")
    class QueVeCadaRol {

        @Test
        @DisplayName("El empleado no ve los gastos ni la rentabilidad del día")
        void mostrador_sinGastosNiResultado() {
            // El endpoint no se le puede cerrar: es el que cuenta el cajón. Pero traía la
            // lista entera de gastos y el resultado del negocio, que es lo que
            // /api/gastos/** y /api/estadisticas/** le prohíben.
            entrarComo("empleado", "ROLE_ADMIN", "ROLE_MOSTRADOR");
            when(cobroRepository.findDeLaJornada(JORNADA))
                    .thenReturn(List.of(cobro("20000", MedioPago.EFECTIVO)));
            when(gastoRepository.totalDeLaJornada(JORNADA)).thenReturn(new BigDecimal("5000"));
            when(gastoRepository.totalDeLaJornadaPorMedio(JORNADA, MedioPago.EFECTIVO))
                    .thenReturn(new BigDecimal("5000"));
            when(gastoService.listarDeLaJornada(JORNADA)).thenReturn(List.of(new GastoResponse()));

            CierreCajaResponse cierre = cajaService.cierreParaQuienPide(JORNADA);

            assertThat(cierre.getGastos()).isEmpty();
            assertThat(cierre.getEgresos()).isNull();
            assertThat(cierre.getResultado()).isNull();
            // Lo que SÍ necesita para poder firmar: sin esto el efectivo esperado no se
            // puede explicar, y un arqueo que no se entiende no se puede firmar.
            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("15000");
            assertThat(cierre.getEgresosEfectivo()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("El dueño ve todo")
        void duenio_veTodo() {
            when(gastoRepository.totalDeLaJornada(JORNADA)).thenReturn(new BigDecimal("5000"));
            when(gastoService.listarDeLaJornada(JORNADA)).thenReturn(List.of(new GastoResponse()));

            CierreCajaResponse cierre = cajaService.cierreParaQuienPide(JORNADA);

            assertThat(cierre.getGastos()).hasSize(1);
            assertThat(cierre.getEgresos()).isEqualByComparingTo("5000");
            assertThat(cierre.getResultado()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Jornada en curso")
    class JornadaEnCurso {

        @Test
        @DisplayName("La jornada actual sale de la disponibilidad, no del reloj")
        void jornadaActual_delegaEnDisponibilidad() {
            assertThat(cajaService.jornadaActual()).isEqualTo(JORNADA);
            verify(disponibilidadCanchaService).fechaDeJornadaActual();
        }
    }

    @Nested
    @DisplayName("Fondo inicial y movimientos sueltos")
    class FondoYMovimientos {

        private MovimientoCaja suelto(ConceptoMovimientoCaja concepto, TipoMovimientoCaja tipo,
                String monto, MedioPago medio) {
            return MovimientoCaja.builder()
                    .id(1L)
                    .jornada(JORNADA)
                    .fecha(LocalDateTime.of(2026, 8, 15, 18, 0))
                    .concepto(concepto)
                    .tipo(tipo)
                    .monto(new BigDecimal(monto))
                    .medio(medio)
                    .registradoPor("empleado")
                    .build();
        }

        @Test
        @DisplayName("El fondo de apertura entra al efectivo esperado")
        void fondo_sumaAlEfectivoEsperado() {
            // Era el motivo por el que el arqueo no cerraba nunca: el efectivo esperado
            // arrancaba de cero y el cambio que el club deja en el cajón aparecía como
            // sobrante todas las noches.
            when(movimientoCajaRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    suelto(ConceptoMovimientoCaja.APERTURA, TipoMovimientoCaja.INGRESO,
                            "20000", MedioPago.EFECTIVO)));
            when(cobroRepository.findDeLaJornada(JORNADA))
                    .thenReturn(List.of(cobro("30000", MedioPago.EFECTIVO)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getFondoInicial()).isEqualByComparingTo("20000");
            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("50000");
            // El fondo NO es facturación: el club no ganó esa plata hoy.
            assertThat(cierre.getTotalDelDia()).isEqualByComparingTo("30000");
            // Y no se muestra dos veces: va en su propio número, no en los ingresos.
            assertThat(cierre.getMovimientosIngreso()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Un depósito al banco saca plata del cajón y no toca la rentabilidad")
        void deposito_bajaElCajonYNoElResultado() {
            when(movimientoCajaRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    suelto(ConceptoMovimientoCaja.DEPOSITO_BANCO, TipoMovimientoCaja.EGRESO,
                            "80000", MedioPago.EFECTIVO)));
            when(cobroRepository.findDeLaJornada(JORNADA))
                    .thenReturn(List.of(cobro("100000", MedioPago.EFECTIVO)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("20000");
            assertThat(cierre.getMovimientosEgreso()).isEqualByComparingTo("80000");
            // La plata sigue siendo del club, solo cambió de lugar.
            assertThat(cierre.getResultado()).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("El alquiler del salón sí es facturación del día")
        void cobroVario_sumaAlResultado() {
            when(movimientoCajaRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    suelto(ConceptoMovimientoCaja.COBRO_VARIO, TipoMovimientoCaja.INGRESO,
                            "45000", MedioPago.EFECTIVO)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getTotalDelDia()).isEqualByComparingTo("45000");
            assertThat(cierre.getResultado()).isEqualByComparingTo("45000");
            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("45000");
        }

        @Test
        @DisplayName("Un movimiento por transferencia no toca el cajón")
        void movimientoNoEfectivo_noTocaElCajon() {
            when(movimientoCajaRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    suelto(ConceptoMovimientoCaja.COBRO_VARIO, TipoMovimientoCaja.INGRESO,
                            "45000", MedioPago.TRANSFERENCIA)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("0");
            assertThat(cierre.getTotalDelDia()).isEqualByComparingTo("45000");
        }

        @Test
        @DisplayName("Al firmar, el fondo y los movimientos quedan congelados")
        void cerrar_congelaElFondo() {
            when(movimientoCajaRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    suelto(ConceptoMovimientoCaja.APERTURA, TipoMovimientoCaja.INGRESO,
                            "20000", MedioPago.EFECTIVO),
                    suelto(ConceptoMovimientoCaja.DEPOSITO_BANCO, TipoMovimientoCaja.EGRESO,
                            "5000", MedioPago.EFECTIVO)));
            when(cierreCajaRepository.existsByFechaAndAnuladoEnIsNull(JORNADA)).thenReturn(false);
            when(cierreCajaRepository.save(any(CierreCaja.class))).thenAnswer(i -> i.getArgument(0));
            CierreCajaRequest request = new CierreCajaRequest();
            request.setFecha(JORNADA);
            request.setEfectivoContado(new BigDecimal("15000"));

            cajaService.cerrar(request);

            ArgumentCaptor<CierreCaja> firmado = ArgumentCaptor.forClass(CierreCaja.class);
            verify(cierreCajaRepository).save(firmado.capture());
            // Sin el fondo congelado, la diferencia firmada no se puede volver a explicar.
            assertThat(firmado.getValue().getFondoInicial()).isEqualByComparingTo("20000");
            assertThat(firmado.getValue().getMovimientosEgreso()).isEqualByComparingTo("5000");
            assertThat(firmado.getValue().getEfectivoEsperado()).isEqualByComparingTo("15000");
            assertThat(firmado.getValue().getDiferencia()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("El desglose por empleado dice quién cobró qué")
        void porUsuario_desglosa() {
            Cobro deJuan = cobro("30000", MedioPago.EFECTIVO);
            deJuan.setRegistradoPor("juan");
            Cobro deAna = cobro("20000", MedioPago.TRANSFERENCIA);
            deAna.setRegistradoPor("ana");
            Venta ventaDeJuan = venta("5000", MedioPago.EFECTIVO);
            ventaDeJuan.setRegistradoPor("juan");
            when(cobroRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(deJuan, deAna));
            when(ventaRepository.findDeLaJornadaConItems(JORNADA)).thenReturn(List.of(ventaDeJuan));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getPorUsuario()).hasSize(2);
            CierreCajaResponse.TotalPorUsuario juan = cierre.getPorUsuario().stream()
                    .filter(fila -> "juan".equals(fila.getUsuario())).findFirst().orElseThrow();
            assertThat(juan.getCobros()).isEqualByComparingTo("30000");
            assertThat(juan.getVentas()).isEqualByComparingTo("5000");
            assertThat(juan.getTotal()).isEqualByComparingTo("35000");
            // Lo que se le pide al entregar el turno es el efectivo, no el total.
            assertThat(juan.getEfectivo()).isEqualByComparingTo("35000");
            assertThat(juan.getOperaciones()).isEqualTo(2);

            CierreCajaResponse.TotalPorUsuario ana = cierre.getPorUsuario().stream()
                    .filter(fila -> "ana".equals(fila.getUsuario())).findFirst().orElseThrow();
            assertThat(ana.getEfectivo()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("El empleado no ve el desglose por empleado")
        void porUsuario_noLoVeElMostrador() {
            entrarComo("empleado", "ROLE_ADMIN", "ROLE_MOSTRADOR");
            Cobro deJuan = cobro("30000", MedioPago.EFECTIVO);
            deJuan.setRegistradoPor("juan");
            when(cobroRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(deJuan));

            CierreCajaResponse cierre = cajaService.cierreParaQuienPide(JORNADA);

            assertThat(cierre.getPorUsuario()).isEmpty();
        }

        @Test
        @DisplayName("Al empleado sí se le muestran todos los movimientos del cajón")
        void movimientosSueltos_losVeElMostrador() {
            // Incluido el retiro del dueño: si se le esconde plata que salió, lo que
            // cuenta no le va a dar nunca contra el efectivo esperado.
            entrarComo("empleado", "ROLE_ADMIN", "ROLE_MOSTRADOR");
            when(movimientoCajaRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    suelto(ConceptoMovimientoCaja.RETIRO_DUENIO, TipoMovimientoCaja.EGRESO,
                            "50000", MedioPago.EFECTIVO)));
            when(movimientoCajaService.aResponse(any())).thenReturn(new MovimientoSueltoResponse());

            CierreCajaResponse cierre = cajaService.cierreParaQuienPide(JORNADA);

            assertThat(cierre.getMovimientosSueltos()).hasSize(1);
            assertThat(cierre.getEfectivoEsperado()).isEqualByComparingTo("-50000");
        }
    }

    @Nested
    @DisplayName("Movimientos del día")
    class MovimientosDelDia {

        @Test
        @DisplayName("Los cobros de un turno de dos horas son una sola línea en la caja")
        void loteDeDosHoras_unaLinea() {
            // Un turno de 2 h son dos reservas con un cobro cada una, registrados en el
            // mismo momento: para el cliente fue un solo pago.
            when(cobroRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    cobro("10000", MedioPago.EFECTIVO),
                    cobro("10000", MedioPago.EFECTIVO)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getMovimientos()).hasSize(1);
            assertThat(cierre.getMovimientos().get(0).getMonto()).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("Los cobros se abren por medio de pago")
        void porMedio_agrupa() {
            when(cobroRepository.findDeLaJornada(JORNADA)).thenReturn(List.of(
                    cobro("20000", MedioPago.EFECTIVO),
                    cobro("30000", MedioPago.TRANSFERENCIA)));
            when(ventaRepository.findDeLaJornadaConItems(JORNADA))
                    .thenReturn(List.of(venta("5000", MedioPago.EFECTIVO)));

            CierreCajaResponse cierre = cajaService.cierre(JORNADA);

            assertThat(cierre.getPorMedio()).hasSize(2);
            assertThat(cierre.getPorMedio().stream()
                    .filter(total -> MedioPago.EFECTIVO.name().equals(total.getMedio()))
                    .findFirst().orElseThrow().getTotal()).isEqualByComparingTo("25000");
        }
    }
}
