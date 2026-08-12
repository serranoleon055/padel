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

    @Transactional(readOnly = true)
    public void exigirDiaAbierto(LocalDate fecha) {
        if (fecha != null && cierreCajaRepository.existsByFecha(fecha)) {
            throw new EstadoInvalidoException("La caja del " + fecha.format(DIA)
                    + " ya está cerrada. Para corregir algo de ese día hay que reabrirla primero.");
        }
    }

    @Transactional(readOnly = true)
    public boolean estaCerrada(LocalDate fecha) {
        return fecha != null && cierreCajaRepository.existsByFecha(fecha);
    }
}
