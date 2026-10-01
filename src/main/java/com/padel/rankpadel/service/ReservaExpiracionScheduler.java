package com.padel.rankpadel.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReservaExpiracionScheduler {

    private final ReservaService reservaService;

    @Scheduled(fixedRate = 180_000)
    public void expirarPendientes() {
        reservaService.expirarPendientesVencidas();
    }

    @Scheduled(fixedRate = 300_000)
    public void finalizarTurnosPasados() {
        reservaService.finalizarTurnosPasados();
    }

    /**
     * Recordatorios de los turnos que se vienen. Cada hora y no una vez por día: un turno
     * sacado hoy a la tarde para mañana temprano tiene que alcanzar a recibirlo.
     */
    @Scheduled(initialDelay = 120_000, fixedRate = 3_600_000)
    public void recordarTurnos() {
        reservaService.enviarRecordatorios();
    }
}
