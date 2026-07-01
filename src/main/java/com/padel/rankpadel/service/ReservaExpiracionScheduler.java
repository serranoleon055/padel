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
}
