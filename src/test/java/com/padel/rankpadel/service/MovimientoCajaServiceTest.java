package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.padel.rankpadel.dto.request.MovimientoCajaRequest;
import com.padel.rankpadel.entity.MovimientoCaja;
import com.padel.rankpadel.enums.ConceptoMovimientoCaja;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoMovimientoCaja;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.MovimientoCajaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("MovimientoCajaService - la plata que no es un turno ni una venta")
class MovimientoCajaServiceTest {

    @Mock
    private MovimientoCajaRepository movimientoCajaRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private CajaCerradaGuard cajaCerradaGuard;
    @Mock
    private DisponibilidadCanchaService disponibilidadCanchaService;

    @InjectMocks
    private MovimientoCajaService movimientoCajaService;

    private static final LocalDate JORNADA = LocalDate.of(2026, 8, 15);

    @BeforeEach
    void setUp() {
        lenient().when(movimientoCajaRepository.save(any(MovimientoCaja.class)))
                .thenAnswer(i -> i.getArgument(0));
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

    private MovimientoCajaRequest pedido(ConceptoMovimientoCaja concepto, String monto) {
        MovimientoCajaRequest request = new MovimientoCajaRequest();
        request.setConcepto(concepto);
        request.setDescripcion("  Lo que sea  ");
        request.setMonto(new BigDecimal(monto));
        request.setMedio(MedioPago.EFECTIVO);
        return request;
    }

    private MovimientoCaja guardado() {
        ArgumentCaptor<MovimientoCaja> captor = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Apertura")
    class Apertura {

        @Test
        @DisplayName("El fondo entra como ingreso de la jornada en curso")
        void abrir_registraElFondo() {
            when(movimientoCajaRepository.existsByJornadaAndConceptoAndAnuladoEnIsNull(
                    JORNADA, ConceptoMovimientoCaja.APERTURA)).thenReturn(false);

            movimientoCajaService.abrir(pedido(ConceptoMovimientoCaja.OTRO, "20000"));

            MovimientoCaja movimiento = guardado();
            // El concepto y el tipo los pone el servidor: abrir la caja es abrir la caja,
            // no lo que haya mandado el formulario.
            assertThat(movimiento.getConcepto()).isEqualTo(ConceptoMovimientoCaja.APERTURA);
            assertThat(movimiento.getTipo()).isEqualTo(TipoMovimientoCaja.INGRESO);
            assertThat(movimiento.getJornada()).isEqualTo(JORNADA);
            assertThat(movimiento.getMonto()).isEqualByComparingTo("20000");
            assertThat(movimiento.getDescripcion()).isEqualTo("Lo que sea");
        }

        @Test
        @DisplayName("La caja de una jornada se abre una sola vez")
        void abrir_dosVeces_rechaza() {
            when(movimientoCajaRepository.existsByJornadaAndConceptoAndAnuladoEnIsNull(
                    JORNADA, ConceptoMovimientoCaja.APERTURA)).thenReturn(true);

            assertThatThrownBy(() -> movimientoCajaService.abrir(
                    pedido(ConceptoMovimientoCaja.APERTURA, "20000")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya está abierta");

            verify(movimientoCajaRepository, never()).save(any());
        }

        @Test
        @DisplayName("El mostrador puede abrir la caja: es el que arranca el día")
        void abrir_loPuedeElMostrador() {
            entrarComo("empleado", "ROLE_ADMIN", "ROLE_MOSTRADOR");
            when(movimientoCajaRepository.existsByJornadaAndConceptoAndAnuladoEnIsNull(
                    JORNADA, ConceptoMovimientoCaja.APERTURA)).thenReturn(false);

            movimientoCajaService.abrir(pedido(ConceptoMovimientoCaja.APERTURA, "20000"));

            assertThat(guardado().getRegistradoPor()).isEqualTo("empleado");
        }
    }

    @Nested
    @DisplayName("Tipo y permisos")
    class TipoYPermisos {

        @Test
        @DisplayName("Un retiro del dueño siempre saca plata, aunque lo manden como ingreso")
        void retiro_esSiempreEgreso() {
            MovimientoCajaRequest request = pedido(ConceptoMovimientoCaja.RETIRO_DUENIO, "50000");
            request.setTipo(TipoMovimientoCaja.INGRESO);

            assertThatThrownBy(() -> movimientoCajaService.registrar(request))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("siempre es egreso");
        }

        @Test
        @DisplayName("Sin tipo, el concepto lo decide cuando puede")
        void concepto_imponeElTipo() {
            movimientoCajaService.registrar(pedido(ConceptoMovimientoCaja.DEPOSITO_BANCO, "80000"));

            assertThat(guardado().getTipo()).isEqualTo(TipoMovimientoCaja.EGRESO);
        }

        @Test
        @DisplayName("Un préstamo va en los dos sentidos, así que hay que elegir")
        void prestamo_sinTipo_rechaza() {
            assertThatThrownBy(() -> movimientoCajaService.registrar(
                    pedido(ConceptoMovimientoCaja.PRESTAMO, "10000")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("entra o sale");
        }

        @Test
        @DisplayName("El empleado no puede cargar un retiro del dueño")
        void retiro_elMostradorNoPuede() {
            // Son los movimientos que sirven para tapar un faltante: si el que cobra los
            // puede cargar, cualquier diferencia del arqueo tiene una explicación a mano.
            entrarComo("empleado", "ROLE_ADMIN", "ROLE_MOSTRADOR");

            assertThatThrownBy(() -> movimientoCajaService.registrar(
                    pedido(ConceptoMovimientoCaja.RETIRO_DUENIO, "50000")))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("dueño");

            verify(movimientoCajaRepository, never()).save(any());
        }

        @Test
        @DisplayName("El empleado sí puede cobrar el alquiler del salón")
        void cobroVario_loPuedeElMostrador() {
            entrarComo("empleado", "ROLE_ADMIN", "ROLE_MOSTRADOR");

            movimientoCajaService.registrar(pedido(ConceptoMovimientoCaja.COBRO_VARIO, "30000"));

            assertThat(guardado().getTipo()).isEqualTo(TipoMovimientoCaja.INGRESO);
        }

        @Test
        @DisplayName("Con el arqueo firmado no se puede mover más plata de esa jornada")
        void jornadaCerrada_rechaza() {
            doThrow(new EstadoInvalidoException("La caja del 15/08/2026 ya está cerrada"))
                    .when(cajaCerradaGuard).exigirDiaAbierto(JORNADA);

            assertThatThrownBy(() -> movimientoCajaService.registrar(
                    pedido(ConceptoMovimientoCaja.COBRO_VARIO, "30000")))
                    .isInstanceOf(EstadoInvalidoException.class);

            verify(movimientoCajaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Anulación")
    class Anulacion {

        private MovimientoCaja existente() {
            return MovimientoCaja.builder()
                    .id(5L)
                    .jornada(JORNADA)
                    .tipo(TipoMovimientoCaja.EGRESO)
                    .concepto(ConceptoMovimientoCaja.DEPOSITO_BANCO)
                    .monto(new BigDecimal("80000"))
                    .medio(MedioPago.EFECTIVO)
                    .build();
        }

        @Test
        @DisplayName("Es baja lógica: la fila queda para poder auditarla")
        void anular_esBajaLogica() {
            MovimientoCaja movimiento = existente();
            when(movimientoCajaRepository.findById(5L)).thenReturn(Optional.of(movimiento));

            movimientoCajaService.anular(5L, "no se depositó");

            assertThat(movimiento.estaAnulado()).isTrue();
            assertThat(movimiento.getAnuladoPor()).isEqualTo("dueño");
            assertThat(movimiento.getMotivoAnulacion()).isEqualTo("no se depositó");
            verify(movimientoCajaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Un movimiento ya anulado no se vuelve a anular")
        void anular_dosVeces_rechaza() {
            MovimientoCaja movimiento = existente();
            movimiento.setAnuladoEn(LocalDateTime.now());
            when(movimientoCajaRepository.findById(5L)).thenReturn(Optional.of(movimiento));

            assertThatThrownBy(() -> movimientoCajaService.anular(5L, "otra vez"))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya está anulado");
        }
    }

    @Nested
    @DisplayName("Signo")
    class Signo {

        @Test
        @DisplayName("El egreso resta del cajón aunque el monto esté en positivo")
        void egreso_aportaEnNegativo() {
            // El monto se guarda siempre positivo y el signo lo da el tipo, así nadie
            // convierte un egreso en ingreso tipeando un menos.
            MovimientoCaja egreso = MovimientoCaja.builder()
                    .tipo(TipoMovimientoCaja.EGRESO).monto(new BigDecimal("80000")).build();
            MovimientoCaja ingreso = MovimientoCaja.builder()
                    .tipo(TipoMovimientoCaja.INGRESO).monto(new BigDecimal("80000")).build();

            assertThat(egreso.aporteConSigno()).isEqualByComparingTo("-80000");
            assertThat(ingreso.aporteConSigno()).isEqualByComparingTo("80000");
        }
    }
}
