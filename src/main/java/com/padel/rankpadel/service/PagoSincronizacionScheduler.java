package com.padel.rankpadel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PagoSincronizacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PagoSincronizacionScheduler.class);

    private final PagoService pagoService;

    @Scheduled(fixedRate = 20000)
    public void sincronizarPendientes() {
        for (String referencia : pagoService.referenciasPendientesRecientes()) {
            try {
                pagoService.sincronizarPago(referencia);
            } catch (RuntimeException e) {
                log.debug("No se pudo sincronizar el pago {}: {}", referencia, e.getMessage());
            }
        }
    }
}
