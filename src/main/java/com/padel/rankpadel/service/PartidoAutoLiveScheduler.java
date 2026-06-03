package com.padel.rankpadel.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.repository.PartidoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Pasa automáticamente a EN_CURSO los partidos cuya fecha/hora programada ya llegó, siempre que el
 * torneo esté iniciado. El partido queda "en vivo" hasta que se cargue el resultado o se declare W.O.
 */
@Component
@RequiredArgsConstructor
public class PartidoAutoLiveScheduler {

    private final PartidoRepository partidoRepository;

    /** Se ejecuta cada minuto. */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void iniciarPartidosProgramados() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Partido> aIniciar = partidoRepository.findParaIniciarAutomatico(ahora);
        for (Partido partido : aIniciar) {
            partido.setEstado(EstadoPartido.EN_CURSO);
            if (partido.getFechaHora() == null) {
                partido.setFechaHora(ahora);
            }
        }
        if (!aIniciar.isEmpty()) {
            partidoRepository.saveAll(aIniciar);
        }
    }
}
