package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.CobroRequest;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Cobro;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoPago;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.enums.MedioCobro;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CobroService - cobro del saldo en el mostrador")
class CobroServiceTest {

    @Mock
    private CobroRepository cobroRepository;
    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private CobroService cobroService;

    private final Cancha cancha = Cancha.builder().id(1L).nombre("Cancha 1").build();

    @BeforeEach
    void setUp() {
        lenient().when(cobroRepository.save(any(Cobro.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** Turno de $20.000 con seña online del 50% ya aprobada: quedan $10.000 por cobrar. */
    private Reserva turnoConSenia(EstadoReserva estado) {
        Pago pago = Pago.builder().estado(EstadoPago.APROBADO).porcentajeSenia(50).build();
        return Reserva.builder()
                .id(4L).cancha(cancha).estado(estado)
                .fecha(LocalDate.now()).horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(21, 0))
                .precioAplicado(new BigDecimal("20000.00"))
                .clienteNombre("Juan")
                .pago(pago)
                .build();
    }

    private CobroRequest cobroDe(String monto) {
        CobroRequest request = new CobroRequest();
        request.setMonto(new BigDecimal(monto));
        request.setMedio(MedioCobro.EFECTIVO);
        return request;
    }

    @Test
    @DisplayName("Cobra el saldo que queda después de la seña online")
    void cobra_saldoRestante() {
        when(reservaRepository.findById(4L)).thenReturn(Optional.of(turnoConSenia(EstadoReserva.FINALIZADA)));
        when(cobroRepository.totalCobradoDe(4L)).thenReturn(BigDecimal.ZERO);

        cobroService.registrar(4L, cobroDe("10000.00"));

        ArgumentCaptor<Cobro> guardado = ArgumentCaptor.forClass(Cobro.class);
        verify(cobroRepository).save(guardado.capture());
        assertThat(guardado.getValue().getMonto()).isEqualByComparingTo("10000.00");
        assertThat(guardado.getValue().getMedio()).isEqualTo(MedioCobro.EFECTIVO);
    }

    @Test
    @DisplayName("Rechaza cobrar más que el saldo pendiente")
    void cobra_deMas_lanza() {
        when(reservaRepository.findById(4L)).thenReturn(Optional.of(turnoConSenia(EstadoReserva.FINALIZADA)));
        when(cobroRepository.totalCobradoDe(4L)).thenReturn(BigDecimal.ZERO);

        // Cobrar de más en el mostrador casi siempre es un error de tipeo.
        assertThatThrownBy(() -> cobroService.registrar(4L, cobroDe("15000.00")))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("10000");
        verify(cobroRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza cobrar un turno que ya está pago")
    void cobra_turnoSaldado_lanza() {
        when(reservaRepository.findById(4L)).thenReturn(Optional.of(turnoConSenia(EstadoReserva.FINALIZADA)));
        when(cobroRepository.totalCobradoDe(4L)).thenReturn(new BigDecimal("10000.00"));

        assertThatThrownBy(() -> cobroService.registrar(4L, cobroDe("1000.00")))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("ya está pago");
    }

    @Test
    @DisplayName("Rechaza cobrar un turno cancelado")
    void cobra_turnoCancelado_lanza() {
        when(reservaRepository.findById(4L)).thenReturn(Optional.of(turnoConSenia(EstadoReserva.CANCELADA)));

        assertThrows(EstadoInvalidoException.class, () -> cobroService.registrar(4L, cobroDe("10000.00")));
        verify(cobroRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un turno sin seña online se cobra entero")
    void cobra_sinSenia_cobraTodo() {
        Reserva sinPago = turnoConSenia(EstadoReserva.FINALIZADA);
        sinPago.setPago(null);
        when(reservaRepository.findById(4L)).thenReturn(Optional.of(sinPago));
        when(cobroRepository.totalCobradoDe(4L)).thenReturn(BigDecimal.ZERO);

        cobroService.registrar(4L, cobroDe("20000.00"));

        verify(cobroRepository).save(any(Cobro.class));
    }

    @Test
    @DisplayName("Se puede cobrar un turno al que el cliente no vino, si el club lo decide")
    void cobra_noShow_permitido() {
        when(reservaRepository.findById(4L)).thenReturn(Optional.of(turnoConSenia(EstadoReserva.NO_SHOW)));
        when(cobroRepository.totalCobradoDe(4L)).thenReturn(BigDecimal.ZERO);

        cobroService.registrar(4L, cobroDe("10000.00"));

        verify(cobroRepository).save(any(Cobro.class));
    }

    @Test
    @DisplayName("Acepta cobros parciales hasta completar el saldo")
    void cobra_enPartes() {
        when(reservaRepository.findById(4L)).thenReturn(Optional.of(turnoConSenia(EstadoReserva.FINALIZADA)));
        when(cobroRepository.totalCobradoDe(4L)).thenReturn(new BigDecimal("6000.00"));

        cobroService.registrar(4L, cobroDe("4000.00"));

        verify(cobroRepository).save(any(Cobro.class));
    }
}
