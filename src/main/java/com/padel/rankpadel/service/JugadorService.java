package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.padel.rankpadel.dto.request.JugadorRequest;
import com.padel.rankpadel.dto.response.JugadorBusquedaResponse;
import com.padel.rankpadel.dto.response.JugadorFichaResponse;
import com.padel.rankpadel.dto.response.JugadorHistorialResponse;
import com.padel.rankpadel.dto.response.JugadorResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionPuntos;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RondaEliminatorias;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.JugadorMapper;
import com.padel.rankpadel.mapper.PartidoMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.ConfiguracionPuntosRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.RankingEntryRepository;
import com.padel.rankpadel.repository.RondaEliminatoriasRepository;
import com.padel.rankpadel.util.NormalizadorRonda;
import com.padel.rankpadel.util.NormalizadorTexto;

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
    private final ConfiguracionPuntosRepository configuracionPuntosRepository;
    private final RondaEliminatoriasRepository rondaEliminatoriasRepository;

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

    @Transactional(readOnly = true)
    public JugadorFichaResponse obtenerFicha(Long id) {
        Jugador jugador = jugadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));
        return JugadorFichaResponse.builder()
                .id(jugador.getId())
                .fechaNacimiento(jugador.getFechaNacimiento())
                .telefono(jugador.getTelefono())
                .build();
    }

    @Transactional(readOnly = true)
    public List<JugadorBusquedaResponse> buscar(String texto) {
        String normalizado = NormalizadorTexto.normalizar(texto);
        if (normalizado.isBlank()) {
            return List.of();
        }
        return jugadorRepository.buscarPorNombreNormalizado(normalizado).stream()
                .map(jugador -> JugadorBusquedaResponse.builder()
                        .id(jugador.getId())
                        .nombre(jugador.getNombre())
                        .apellido(jugador.getApellido())
                        .categoriaNombre(jugador.getCategoria() != null ? jugador.getCategoria().getNombre() : null)
                        .genero(jugador.getGenero())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public JugadorResponse crear(JugadorRequest request) {

        Categoria categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.getCategoriaId()));
        }

        String nombreNormalizado = NormalizadorTexto.normalizarNombre(request.getNombre(), request.getApellido());
        if (jugadorRepository.existsByActivoTrueAndNombreNormalizado(nombreNormalizado)) {
            throw new EstadoInvalidoException("Ya existe un jugador llamado \"" + request.getNombre() + " "
                    + request.getApellido() + "\". Usá un nombre distinto o editá el existente.");
        }

        Jugador jugador = jugadorMapper.requestToJugador(request, categoria);

        jugadorRepository.save(jugador);

        return jugadorMapper.jugadorToResponse(jugador);
    }

    @Transactional
    public JugadorResponse actualizar(Long id, JugadorRequest request) {
        Jugador jugadorExistente = jugadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));

        String nombreNormalizado = NormalizadorTexto.normalizarNombre(request.getNombre(), request.getApellido());
        if (jugadorRepository.existsByActivoTrueAndNombreNormalizadoAndIdNot(nombreNormalizado, id)) {
            throw new EstadoInvalidoException("Ya existe otro jugador llamado \"" + request.getNombre() + " "
                    + request.getApellido() + "\". Usá un nombre distinto.");
        }

        Categoria categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.getCategoriaId()));
        }

        Jugador jugadorActualizado = jugadorMapper.requestToJugador(request, categoria);
        jugadorActualizado.setId(id);
        jugadorActualizado.setFechaRegistro(jugadorExistente.getFechaRegistro());
        jugadorActualizado.setActivo(jugadorExistente.isActivo());
        if (request.getTelefono() == null || request.getTelefono().isBlank()) {
            jugadorActualizado.setTelefono(jugadorExistente.getTelefono());
        }
        if (request.getFechaNacimiento() == null) {
            jugadorActualizado.setFechaNacimiento(jugadorExistente.getFechaNacimiento());
        }

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

        var ranking = rankingEntryRepository.findByJugadorId(id).stream()
                .filter(entry -> entry.getCategoria() != null)
                .map(entry -> entry.getCategoria().getId())
                .distinct()
                .flatMap(categoriaId -> rankingService.obtenerRanking(categoriaId, null).stream())
                .filter(rankingItem -> rankingItem.getJugadorId().equals(id))
                .collect(Collectors.toList());

        List<Partido> partidos = partidoRepository.findPartidosFinalizadosByJugadorId(id);

        List<Pareja> parejas = parejaRepository.findByJugador(id);

        Map<Long, JugadorHistorialResponse.TorneoHistorialItem> torneoMap = new LinkedHashMap<>();
        for (Pareja pareja : parejas) {
            if (pareja.getTorneo() == null) continue;
            Long torneoId = pareja.getTorneo().getId();
            if (torneoMap.containsKey(torneoId)) continue;

            Long categoriaId = pareja.getCategoria() != null ? pareja.getCategoria().getId() : null;

            List<Partido> partidosDelTorneo = partidos.stream()
                    .filter(p -> p.getTorneo() != null && p.getTorneo().getId().equals(torneoId))
                    .collect(Collectors.toList());

            if (partidosDelTorneo.isEmpty()) continue;

            Map<String, ConfiguracionPuntos> configPorRonda = configuracionPuntosRepository
                    .findByTorneoIdOrderByOrden(torneoId).stream()
                    .collect(Collectors.toMap(config -> NormalizadorRonda.normalizar(config.getNombreRonda()), config -> config, (a, b) -> a));

            int puntos = 0;
            for (Partido partido : partidosDelTorneo) {
                String nombreRonda = partido.getFase() == FasePartido.GRUPOS || partido.getRonda() == null
                        ? "Grupos"
                        : partido.getRonda().getNombre();
                ConfiguracionPuntos config = configPorRonda.get(NormalizadorRonda.normalizar(nombreRonda));
                if (config == null) continue;
                boolean ganoPartido = partido.getGanador() != null && partido.getGanador().getId().equals(pareja.getId());
                puntos += ganoPartido ? config.getPuntosGanador() : config.getPuntosPerdedor();
            }

            String mejorRonda = partidosDelTorneo.stream()
                    .filter(p -> p.getRonda() != null)
                    .max(Comparator.comparingInt(p -> p.getRonda().getOrden()))
                    .map(p -> p.getRonda().getNombre())
                    .orElse(partidosDelTorneo.isEmpty() ? "—" : "Fase de grupos");

            boolean fueGanador = false;
            if (categoriaId != null) {
                List<RondaEliminatorias> rondas = rondaEliminatoriasRepository
                        .findByTorneoIdAndCategoriaIdOrderByOrden(torneoId, categoriaId);
                if (!rondas.isEmpty()) {
                    int ordenFinal = rondas.get(rondas.size() - 1).getOrden();
                    fueGanador = partidosDelTorneo.stream()
                            .filter(p -> p.getRonda() != null && p.getRonda().getOrden() == ordenFinal)
                            .anyMatch(p -> p.getGanador() != null && p.getGanador().getId().equals(pareja.getId()));
                }
            }

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

        Set<String> estadosAgenda = Set.of("INSCRIPCION", "SORTEADO", "EN_CURSO");
        Map<Long, JugadorHistorialResponse.TorneoHistorialItem> agendaMap = new LinkedHashMap<>();
        for (Pareja pareja : parejas) {
            var torneo = pareja.getTorneo();
            if (torneo == null || torneo.getEstado() == null) continue;
            if (!estadosAgenda.contains(torneo.getEstado().name())) continue;
            if (agendaMap.containsKey(torneo.getId())) continue;
            agendaMap.put(torneo.getId(), JugadorHistorialResponse.TorneoHistorialItem.builder()
                    .torneoId(torneo.getId())
                    .torneoNombre(torneo.getNombre())
                    .categoriaNombre(pareja.getCategoria() != null ? pareja.getCategoria().getNombre() : null)
                    .estado(torneo.getEstado().name())
                    .fechaInicio(torneo.getFechaInicio() != null ? torneo.getFechaInicio().toString() : null)
                    .fechaFin(torneo.getFechaFin() != null ? torneo.getFechaFin().toString() : null)
                    .fueGanador(false)
                    .mejorRonda("—")
                    .puntosObtenidos(0)
                    .build());
        }
        List<JugadorHistorialResponse.TorneoHistorialItem> agenda = new ArrayList<>(agendaMap.values());
        agenda.sort(Comparator.comparing(JugadorHistorialResponse.TorneoHistorialItem::getFechaInicio,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return JugadorHistorialResponse.builder()
                .jugador(jugadorMapper.jugadorToResponse(jugador))
                .ranking(ranking)
                .partidos(partidos.stream().map(partidoMapper::partidoToResponse).collect(Collectors.toList()))
                .torneos(torneos)
                .agenda(agenda)
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
