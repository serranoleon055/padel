package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.padel.rankpadel.dto.request.JugadorRequest;
import com.padel.rankpadel.dto.response.JugadorHistorialResponse;
import com.padel.rankpadel.dto.response.JugadorResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RankingEntry;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.JugadorMapper;
import com.padel.rankpadel.mapper.PartidoMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.RankingEntryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JugadorService {

    private final JugadorRepository jugadorRepository;
    private final CategoriaRepository categoriaRepository;
    private final JugadorMapper jugadorMapper;
    private final ImageStorageService imageStorageService;
    private final PartidoRepository partidoRepository;
    private final ParejaRepository parejaRepository;
    private final RankingEntryRepository rankingEntryRepository;
    private final RankingService rankingService;
    private final PartidoMapper partidoMapper;

    @Transactional(readOnly = true)
    public List<JugadorResponse> listarTodos() {
        return jugadorRepository.findAllConCategoria()
                .stream()
                .map(jugadorMapper::jugadorToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JugadorResponse buscarPorId(Long id) {
        Jugador jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));
        if (!jugador.isActivo()) {
            throw new ResourceNotFoundException("Jugador", id);
        }
        JugadorResponse jugadorDTO = jugadorMapper.jugadorToResponse(jugador);
        return jugadorDTO;
    }

    @Transactional
    public JugadorResponse crear(JugadorRequest request) {

        Categoria categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.getCategoriaId()));
        }

        Jugador jugador = jugadorMapper.requestToJugador(request, categoria);

        jugadorRepository.save(jugador);

        return jugadorMapper.jugadorToResponse(jugador);
    }

    @Transactional
    public JugadorResponse actualizar(Long id, JugadorRequest request) {
        Jugador jugadorExistente = jugadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));

        Categoria categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.getCategoriaId()));
        }

        Jugador jugadorActualizado = jugadorMapper.requestToJugador(request, categoria);
        jugadorActualizado.setId(id);
        jugadorActualizado.setFechaRegistro(jugadorExistente.getFechaRegistro());
        jugadorActualizado.setActivo(jugadorExistente.isActivo());

        jugadorRepository.save(jugadorActualizado);
        return jugadorMapper.jugadorToResponse(jugadorActualizado);
    }

    @Transactional
    public void eliminar(Long id) {
        Jugador jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));
        jugador.setActivo(false);
        jugadorRepository.save(jugador);
    }

    @Transactional
    public void eliminarBatch(List<Long> ids) {
        List<Jugador> jugadores = jugadorRepository.findAllById(ids);
        jugadores.forEach(jugador -> jugador.setActivo(false));
        jugadorRepository.saveAll(jugadores);
    }

    @Transactional(readOnly = true)
    public JugadorHistorialResponse obtenerHistorial(Long id) {
        Jugador jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));
        if (!jugador.isActivo()) throw new ResourceNotFoundException("Jugador", id);

        // Ranking actual por categoría, con la misma posición que ve el público en cada tabla.
        var ranking = rankingEntryRepository.findByJugadorId(id).stream()
                .filter(entry -> entry.getCategoria() != null)
                .map(entry -> entry.getCategoria().getId())
                .distinct()
                .flatMap(categoriaId -> rankingService.obtenerRanking(categoriaId, null).stream())
                .filter(rankingItem -> rankingItem.getJugadorId().equals(id))
                .collect(Collectors.toList());

        // Partidos finalizados
        List<Partido> partidos = partidoRepository.findPartidosFinalizadosByJugadorId(id);

        // Construir historial de torneos agrupando las parejas del jugador (solo torneos activos)
        List<Pareja> parejas = parejaRepository.findAll().stream()
                .filter(p -> (p.getJugador1() != null && p.getJugador1().getId().equals(id))
                        || (p.getJugador2() != null && p.getJugador2().getId().equals(id)))
                .filter(p -> p.getTorneo() != null && p.getTorneo().isActivo())
                .collect(Collectors.toList());

        Map<Long, JugadorHistorialResponse.TorneoHistorialItem> torneoMap = new LinkedHashMap<>();
        for (Pareja pareja : parejas) {
            if (pareja.getTorneo() == null) continue;
            Long torneoId = pareja.getTorneo().getId();
            if (torneoMap.containsKey(torneoId)) continue;

            // Puntos obtenidos en este torneo
            int puntos = rankingEntryRepository
                    .findByJugadorIdAndCategoriaIdAndTemporadaIsNull(id, pareja.getCategoria().getId())
                    .map(RankingEntry::getPuntosTotales).orElse(0);

            // Mejor ronda: encontrar el partido más avanzado ganado
            String mejorRonda = partidos.stream()
                    .filter(p -> p.getTorneo() != null && p.getTorneo().getId().equals(torneoId))
                    .filter(p -> p.getGanador() != null && p.getGanador().getId().equals(pareja.getId()))
                    .map(p -> p.getRonda() != null ? p.getRonda().getNombre()
                            : (p.getGrupo() != null ? p.getGrupo().getNombre() : "Grupos"))
                    .reduce((a, b) -> b)  // último (más avanzado) en orden de creación
                    .orElse("—");

            boolean fueGanador = partidos.stream()
                    .filter(p -> p.getTorneo() != null && p.getTorneo().getId().equals(torneoId))
                    .filter(p -> p.getRonda() != null && "Final".equalsIgnoreCase(p.getRonda().getNombre()))
                    .anyMatch(p -> p.getGanador() != null && p.getGanador().getId().equals(pareja.getId()));

            torneoMap.put(torneoId, JugadorHistorialResponse.TorneoHistorialItem.builder()
                    .torneoId(torneoId)
                    .torneoNombre(pareja.getTorneo().getNombre())
                    .categoriaNombre(pareja.getCategoria() != null ? pareja.getCategoria().getNombre() : null)
                    .estado(pareja.getTorneo().getEstado() != null ? pareja.getTorneo().getEstado().name() : null)
                    .fechaInicio(pareja.getTorneo().getFechaInicio() != null ? pareja.getTorneo().getFechaInicio().toString() : null)
                    .fechaFin(pareja.getTorneo().getFechaFin() != null ? pareja.getTorneo().getFechaFin().toString() : null)
                    .fueGanador(fueGanador)
                    .mejorRonda(mejorRonda)
                    .puntosObtenidos(puntos)
                    .build());
        }

        List<JugadorHistorialResponse.TorneoHistorialItem> torneos = new ArrayList<>(torneoMap.values());
        torneos.sort(Comparator.comparing(JugadorHistorialResponse.TorneoHistorialItem::getFechaInicio,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return JugadorHistorialResponse.builder()
                .jugador(jugadorMapper.jugadorToResponse(jugador))
                .ranking(ranking)
                .partidos(partidos.stream().map(partidoMapper::partidoToResponse).collect(Collectors.toList()))
                .torneos(torneos)
                .build();
    }

    @Transactional
    public JugadorResponse actualizarFoto(Long id, MultipartFile file) {
        Jugador jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));
        if (!jugador.isActivo()) {
            throw new ResourceNotFoundException("Jugador", id);
        }

        jugador.setFotoUrl(imageStorageService.guardarJugadorFoto(id, file));
        jugadorRepository.save(jugador);
        return jugadorMapper.jugadorToResponse(jugador);
    }

    @Transactional
    public JugadorResponse eliminarFoto(Long id) {
        Jugador jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));
        if (!jugador.isActivo()) {
            throw new ResourceNotFoundException("Jugador", id);
        }

        imageStorageService.borrarPorUrl(jugador.getFotoUrl());
        jugador.setFotoUrl(null);
        jugadorRepository.save(jugador);
        return jugadorMapper.jugadorToResponse(jugador);
    }

}
