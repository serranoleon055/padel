package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.ConceptoPago;
import com.padel.rankpadel.enums.EstadoPago;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.repository.PagoRepository;
import com.padel.rankpadel.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PagoService - confirmación de pagos aprobados")
class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private PagoService pagoService;

    private Pago pagoReservaPendiente() {
        return Pago.builder()
                .id(7L)
                .concepto(ConceptoPago.RESERVA)
                .estado(EstadoPago.PENDIENTE)
                .montoSenia(new BigDecimal("10000"))
                .referenciaExterna("ref-123")
                .clienteNombre("Juan")
                .clienteTelefono("3851234567")
                .build();
    }

    private Reserva reserva(EstadoReserva estado) {
        return Reserva.builder()
                .id(1L)
                .estado(estado)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(20, 0))
                .horaFin(LocalTime.of(21, 0))
                .build();
    }

    @Test
    @DisplayName("Confirma el turno cuando el pago entra a tiempo")
    void pagoATiempo_confirmaElTurno() {
        Pago pago = pagoReservaPendiente();
        Reserva reserva = reserva(EstadoReserva.PENDIENTE);
        when(pagoRepository.findByReferenciaExterna("ref-123")).thenReturn(Optional.of(pago));
        when(reservaRepository.findByPagoId(7L)).thenReturn(List.of(reserva));

        pagoService.confirmarPagoAprobado("ref-123", "99");

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(notificacionService, never()).avisarPagoSinTurno(any(), anyList());
    }

    @Test
    @DisplayName("Si el pago entra tarde y el turno ya expiró, marca APROBADO_SIN_TURNO y avisa al club")
    void pagoTardio_turnoExpirado_marcaYAvisa() {
        Pago pago = pagoReservaPendiente();
        Reserva expirada = reserva(EstadoReserva.EXPIRADA);
        when(pagoRepository.findByReferenciaExterna("ref-123")).thenReturn(Optional.of(pago));
        when(reservaRepository.findByPagoId(7L)).thenReturn(List.of(expirada));

        pagoService.confirmarPagoAprobado("ref-123", "99");

        // Antes esto se salteaba en silencio: el club se quedaba con la seña de una cancha
        // que nunca entregó y nadie se enteraba.
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO_SIN_TURNO);
        assertThat(expirada.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        verify(notificacionService).avisarPagoSinTurno(any(Pago.class), anyList());
    }

    @Test
    @DisplayName("Un pago ya marcado como APROBADO_SIN_TURNO no se vuelve a procesar")
    void pagoYaMarcadoSinTurno_noSeReprocesa() {
        Pago pago = pagoReservaPendiente();
        pago.setEstado(EstadoPago.APROBADO_SIN_TURNO);
        when(pagoRepository.findByReferenciaExterna("ref-123")).thenReturn(Optional.of(pago));

        pagoService.confirmarPagoAprobado("ref-123", "99");

        verify(reservaRepository, never()).findByPagoId(any());
        verify(notificacionService, never()).avisarPagoSinTurno(any(), anyList());
    }
}
