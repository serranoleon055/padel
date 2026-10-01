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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.GastoRequest;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.enums.CategoriaGasto;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.GastoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("GastoService - egresos del club")
class GastoServiceTest {

    @Mock
    private GastoRepository gastoRepository;
    @Mock
    private CajaCerradaGuard cajaCerradaGuard;
    @Mock
    private DisponibilidadCanchaService disponibilidadCanchaService;

    @InjectMocks
    private GastoService gastoService;

    /** La noche del sábado: el club abrió el 15 y cierra a las 2 del 16. */
    private static final LocalDate JORNADA = LocalDate.of(2026, 8, 15);

    @BeforeEach
    void setUp() {
        lenient().when(gastoRepository.save(any(Gasto.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(disponibilidadCanchaService.fechaDeJornadaActual()).thenReturn(JORNADA);
    }

    private GastoRequest pedido(LocalDate fecha, String monto, MedioPago medio) {
        GastoRequest request = new GastoRequest();
        request.setFecha(fecha);
        request.setCategoria(CategoriaGasto.LUZ);
        request.setDescripcion("  Luz de julio  ");
        request.setMonto(new BigDecimal(monto));
        request.setMedio(medio);
        return request;
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("La jornada es la que el club está atendiendo, no la fecha contable")
        void registrar_estampaLaJornadaEnCurso() {
            // Una factura de julio pagada hoy del cajón: pesa en julio para el resultado
            // del mes, pero el efectivo falta en el arqueo de ESTA noche. Si la jornada
            // saliera de la fecha contable, ese faltante aparecería en un arqueo de julio
            // que ya está firmado.
            gastoService.registrar(pedido(LocalDate.of(2026, 7, 10), "50000", MedioPago.EFECTIVO));

            ArgumentCaptor<Gasto> guardado = ArgumentCaptor.forClass(Gasto.class);
            verify(gastoRepository).save(guardado.capture());
            assertThat(guardado.getValue().getFecha()).isEqualTo(LocalDate.of(2026, 7, 10));
            assertThat(guardado.getValue().getJornada()).isEqualTo(JORNADA);
        }

        @Test
        @DisplayName("Un gasto con fecha futura no entra")
        void registrar_fechaFutura_rechaza() {
            LocalDate manana = LocalDate.now().plusDays(1);

            assertThatThrownBy(() -> gastoService.registrar(pedido(manana, "50000", MedioPago.EFECTIVO)))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("futura");

            verify(gastoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Con el arqueo de la jornada firmado, no se puede cargar un egreso")
        void registrar_jornadaCerrada_rechaza() {
            doThrow(new EstadoInvalidoException("La caja del 15/08/2026 ya está cerrada"))
                    .when(cajaCerradaGuard).exigirDiaAbierto(JORNADA);

            assertThatThrownBy(() -> gastoService.registrar(
                    pedido(LocalDate.of(2026, 8, 15), "50000", MedioPago.EFECTIVO)))
                    .isInstanceOf(EstadoInvalidoException.class);

            verify(gastoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Un gasto normal no es mercadería")
        void registrar_noEsMercaderia() {
            gastoService.registrar(pedido(LocalDate.of(2026, 8, 15), "50000", MedioPago.EFECTIVO));

            ArgumentCaptor<Gasto> guardado = ArgumentCaptor.forClass(Gasto.class);
            verify(gastoRepository).save(guardado.capture());
            assertThat(guardado.getValue().isEsMercaderia()).isFalse();
            assertThat(guardado.getValue().getDescripcion()).isEqualTo("Luz de julio");
        }

        @Test
        @DisplayName("La compra de mercadería entra marcada como inventario")
        void registrarEntidad_mercaderia_marcaElFlag() {
            // El flag es lo que separa el inventario del gasto operativo en el estado de
            // resultados. Mirando `producto` no alcanza: una compra con varios productos
            // en un comprobante no puede apuntar a uno.
            gastoService.registrarEntidad(
                    pedido(LocalDate.of(2026, 8, 15), "135000", MedioPago.EFECTIVO), null, true);

            ArgumentCaptor<Gasto> guardado = ArgumentCaptor.forClass(Gasto.class);
            verify(gastoRepository).save(guardado.capture());
            assertThat(guardado.getValue().isEsMercaderia()).isTrue();
        }
    }

    @Nested
    @DisplayName("Edición y anulación")
    class EdicionYAnulacion {

        private Gasto existente() {
            return Gasto.builder()
                    .id(7L)
                    .fecha(LocalDate.of(2026, 8, 15))
                    .jornada(JORNADA)
                    .categoria(CategoriaGasto.LUZ)
                    .descripcion("Luz")
                    .monto(new BigDecimal("50000"))
                    .medio(MedioPago.EFECTIVO)
                    .build();
        }

        @Test
        @DisplayName("Corregir a qué mes pesa un gasto no mueve su jornada de caja")
        void actualizar_noMueveLaJornada() {
            Gasto gasto = existente();
            when(gastoRepository.findById(7L)).thenReturn(Optional.of(gasto));

            gastoService.actualizar(7L, pedido(LocalDate.of(2026, 7, 1), "50000", MedioPago.EFECTIVO));

            assertThat(gasto.getFecha()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(gasto.getJornada()).isEqualTo(JORNADA);
        }

        @Test
        @DisplayName("Un gasto de una jornada arqueada no se puede editar")
        void actualizar_jornadaCerrada_rechaza() {
            when(gastoRepository.findById(7L)).thenReturn(Optional.of(existente()));
            doThrow(new EstadoInvalidoException("cerrada")).when(cajaCerradaGuard).exigirDiaAbierto(JORNADA);

            assertThatThrownBy(() -> gastoService.actualizar(7L,
                    pedido(LocalDate.of(2026, 8, 15), "60000", MedioPago.EFECTIVO)))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        @DisplayName("Anular es baja lógica: la fila queda para poder auditarla")
        void anular_esBajaLogica() {
            Gasto gasto = existente();
            when(gastoRepository.findById(7L)).thenReturn(Optional.of(gasto));

            gastoService.anular(7L, "lo cargué dos veces");

            assertThat(gasto.getAnuladoEn()).isNotNull();
            assertThat(gasto.getMotivoAnulacion()).isEqualTo("lo cargué dos veces");
            assertThat(gasto.estaAnulado()).isTrue();
            verify(gastoRepository, never()).delete(any());
            verify(gastoRepository).save(gasto);
        }

        @Test
        @DisplayName("Un gasto ya anulado no se vuelve a anular")
        void anular_dosVeces_rechaza() {
            Gasto gasto = existente();
            gasto.setAnuladoEn(LocalDateTime.now());
            when(gastoRepository.findById(7L)).thenReturn(Optional.of(gasto));

            assertThatThrownBy(() -> gastoService.anular(7L, "otra vez"))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya está anulado");
        }
    }
}
