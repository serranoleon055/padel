package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CierreCajaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CajaCerradaGuard - una jornada arqueada no se toca más")
class CajaCerradaGuardTest {

    @Mock
    private CierreCajaRepository cierreCajaRepository;

    @InjectMocks
    private CajaCerradaGuard guard;

    private static final LocalDate JORNADA = LocalDate.of(2026, 8, 15);

    @Test
    @DisplayName("Con el arqueo firmado, no se puede mover plata de esa jornada")
    void jornadaCerrada_rechaza() {
        when(cierreCajaRepository.existsByFechaAndAnuladoEnIsNull(JORNADA)).thenReturn(true);

        assertThatThrownBy(() -> guard.exigirDiaAbierto(JORNADA))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("15/08/2026")
                .hasMessageContaining("reabrirla");
    }

    @Test
    @DisplayName("Sin arqueo firmado, la jornada está abierta")
    void jornadaAbierta_pasa() {
        when(cierreCajaRepository.existsByFechaAndAnuladoEnIsNull(JORNADA)).thenReturn(false);

        assertThatCode(() -> guard.exigirDiaAbierto(JORNADA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un arqueo reabierto no bloquea: justamente se reabrió para corregir")
    void arqueoReabierto_noBloquea() {
        // El arqueo anulado sigue en la tabla desde V59. Si contara como cierre, reabrir
        // no serviría para nada: la jornada quedaría bloqueada para siempre.
        when(cierreCajaRepository.existsByFechaAndAnuladoEnIsNull(JORNADA)).thenReturn(false);

        assertThatCode(() -> guard.exigirDiaAbierto(JORNADA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sin jornada no hay nada que chequear")
    void jornadaNula_noConsulta() {
        assertThatCode(() -> guard.exigirDiaAbierto(null)).doesNotThrowAnyException();

        verify(cierreCajaRepository, never()).existsByFechaAndAnuladoEnIsNull(null);
    }
}
