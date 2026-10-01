package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CierreCajaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Una vez que alguien contó el cajón y firmó el cierre de un día, los movimientos de esa
 * fecha no se tocan más: si se pudieran, el arqueo firmado dejaría de cuadrar con lo que
 * el sistema dice, y la diferencia que se registró perdería sentido.
 *
 * <p>Vive aparte de {@link CajaService} porque lo usan los servicios de cobros, ventas y
 * gastos, y {@code CajaService} depende de ellos: ponerlo ahí sería un ciclo.
 */
@Service
@RequiredArgsConstructor
public class CajaCerradaGuard {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CierreCajaRepository cierreCajaRepository;

    /**
     * @param jornada jornada del club a la que entraría el movimiento, no el día de
     *                calendario: a las 00:30 se sigue trabajando sobre la noche de ayer
     */
    @Transactional(readOnly = true)
    public void exigirDiaAbierto(LocalDate jornada) {
        // Solo el arqueo vigente cierra la jornada. Los reabiertos quedan en la tabla
        // desde V59 y no tienen que bloquear nada: justamente se reabrieron para corregir.
        if (jornada != null && cierreCajaRepository.existsByFechaAndAnuladoEnIsNull(jornada)) {
            throw new EstadoInvalidoException("La caja del " + jornada.format(DIA)
                    + " ya está cerrada. Para corregir algo de ese día hay que reabrirla primero.");
        }
    }
}
