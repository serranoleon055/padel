package com.padel.rankpadel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.response.GeneracionTurnosFijosResponse;

import lombok.RequiredArgsConstructor;

/**
 * Mantiene generadas las reservas de los turnos fijos. Corre al arrancar y una vez por
 * día de madrugada: la generación es idempotente, así que repetirla no duplica nada.
 */
@Component
@RequiredArgsConstructor
public class TurnoFijoScheduler {

    private static final Logger log = LoggerFactory.getLogger(TurnoFijoScheduler.class);

    private final TurnoFijoService turnoFijoService;

    @Scheduled(initialDelay = 60_000, fixedRate = 86_400_000)
    public void generar() {
        try {
            GeneracionTurnosFijosResponse resultado = turnoFijoService.generarTodos();
            if (resultado.getGeneradas() > 0 || !resultado.getConflictos().isEmpty()) {
                log.info("Turnos fijos: {} reservas generadas, {} conflictos",
                        resultado.getGeneradas(), resultado.getConflictos().size());
            }
        } catch (RuntimeException e) {
            log.error("No se pudieron generar los turnos fijos: {}", e.getMessage(), e);
        }
    }
}
