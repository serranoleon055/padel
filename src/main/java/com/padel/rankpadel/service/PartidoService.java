package com.padel.rankpadel.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.ProgramarPartidoRequest;
import com.padel.rankpadel.dto.request.WalkoverRequest;
import com.padel.rankpadel.dto.response.PartidoResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.PartidoMapper;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartidoService {

    private final PartidoRepository partidoRepository;
    private final PartidoMapper partidoMapper;
    private final TorneoRepository torneoRepository;
    private final CanchaRepository canchaRepository;
    private final ParejaRepository parejaRepository;
    private final ResultadoService resultadoService;

    @Transactional(readOnly = true)
    public List<PartidoResponse> listarPorTorneo(Long torneoId) {

        torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        List<Partido> partidos = partidoRepository.findByTorneoId(torneoId);

        return partidos.stream()
                .map(partidoMapper::partidoToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PartidoResponse> listarUltimosResultados() {
        return partidoRepository.findTop10ByEstadoOrderByFechaHoraDescIdDesc(EstadoPartido.FINALIZADO).stream()
                .map(partidoMapper::partidoToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PartidoResponse> listarEnVivo() {
        return partidoRepository.findTop10ByEstadoOrderByFechaHoraDescIdDesc(EstadoPartido.EN_CURSO).stream()
                .map(partidoMapper::partidoToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PartidoResponse iniciarPartido(Long torneoId, Long partidoId) {
        Partido partido = partidoRepository.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", partidoId));

        if (!partido.getTorneo().getId().equals(torneoId)) {
            throw new EstadoInvalidoException("El partido no pertenece al torneo indicado");
        }

        if (!partido.getEstado().equals(EstadoPartido.PENDIENTE)) {
            throw new EstadoInvalidoException("El partido debe estar en estado PENDIENTE para iniciarse");
        }

        partido.setEstado(EstadoPartido.EN_CURSO);
        partidoRepository.save(partido);

        return partidoMapper.partidoToResponse(partido);
    }

    @Transactional(readOnly = true)
    public List<PartidoResponse> calendarioPorTorneo(Long torneoId) {
        torneoRepository.findById(torneoId)
                .orElseThrow(() -> new EstadoInvalidoException("Torneo no encontrado"));
        return partidoRepository.findCalendarioPorTorneo(torneoId).stream()
                .map(partidoMapper::partidoToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PartidoResponse> listarProximos() {
        return partidoRepository.findProximos(LocalDateTime.now()).stream()
                .limit(20)
                .map(partidoMapper::partidoToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PartidoResponse programarPartido(Long torneoId, Long partidoId, ProgramarPartidoRequest request) {
        Partido partido = partidoRepository.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", partidoId));

        if (!partido.getTorneo().getId().equals(torneoId)) {
            throw new EstadoInvalidoException("El partido no pertenece al torneo indicado");
        }

        if (partido.getEstado().equals(EstadoPartido.FINALIZADO)
                || partido.getEstado().equals(EstadoPartido.BYE)
                || partido.getEstado().equals(EstadoPartido.WALKOVER)
                || partido.getEstado().equals(EstadoPartido.RETIRO)) {
            throw new EstadoInvalidoException("No se puede programar un partido ya finalizado");
        }

        partido.setFechaHoraProgramada(request.getFechaHora());

        if (request.getCanchaId() != null) {
            Cancha cancha = canchaRepository.findById(request.getCanchaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cancha", request.getCanchaId()));
            partido.setCancha(cancha);
        }

        partidoRepository.save(partido);
        return partidoMapper.partidoToResponse(partido);
    }

    /**
     * Declara un walkover o retiro.
     * - WALKOVER (W.O.): la pareja no se presentó antes del partido. No se otorgan puntos.
     * - RETIRO: una pareja abandonó durante el partido. El ganador recibe puntos normales.
     */
    @Transactional
    public PartidoResponse declararWalkoverORetiro(Long torneoId, Long partidoId, WalkoverRequest request) {
        Partido partido = partidoRepository.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", partidoId));

        if (!partido.getTorneo().getId().equals(torneoId)) {
            throw new EstadoInvalidoException("El partido no pertenece al torneo indicado");
        }

        if (!partido.getTorneo().getEstado().equals(com.padel.rankpadel.enums.EstadoTorneo.EN_CURSO)) {
            throw new EstadoInvalidoException("El torneo debe estar iniciado para declarar W.O. o retiro");
        }

        if (partido.getEstado().equals(EstadoPartido.FINALIZADO)
                || partido.getEstado().equals(EstadoPartido.BYE)
                || partido.getEstado().equals(EstadoPartido.WALKOVER)
                || partido.getEstado().equals(EstadoPartido.RETIRO)) {
            throw new EstadoInvalidoException("El partido ya está en un estado terminal");
        }

        EstadoPartido nuevoEstado;
        try {
            nuevoEstado = EstadoPartido.valueOf(request.getTipo().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EstadoInvalidoException("Tipo inválido. Use WALKOVER o RETIRO");
        }
        if (nuevoEstado != EstadoPartido.WALKOVER && nuevoEstado != EstadoPartido.RETIRO) {
            throw new EstadoInvalidoException("Tipo inválido. Use WALKOVER o RETIRO");
        }

        Pareja ganador = parejaRepository.findById(request.getGanadorParejaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pareja ganadora", request.getGanadorParejaId()));

        boolean ganadorEsLocal = partido.getLocal() != null && partido.getLocal().getId().equals(ganador.getId());
        boolean ganadorEsVisitante = partido.getVisitante() != null && partido.getVisitante().getId().equals(ganador.getId());
        if (!ganadorEsLocal && !ganadorEsVisitante) {
            throw new EstadoInvalidoException("La pareja ganadora no participa en este partido");
        }

        partido.setGanador(ganador);
        partido.setEstado(nuevoEstado);
        partidoRepository.save(partido);

        // El RETIRO sí otorga puntos (el rival abandonó en juego); el WALKOVER no
        if (nuevoEstado == EstadoPartido.RETIRO) {
            resultadoService.actualizarRankingWO(partido);
        }

        resultadoService.avanzarBracketDespuesDeWO(partido);

        return partidoMapper.partidoToResponse(partido);
    }

}
