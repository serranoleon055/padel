package com.padel.rankpadel.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.ParejaRequest;
import com.padel.rankpadel.dto.response.ParejaResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.ParejaMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParejaService {

    private final ParejaRepository parejaRepository;
    private final ParejaMapper parejaMapper;
    private final TorneoRepository torneoRepository;
    private final JugadorRepository jugadorRepository;
    private final CategoriaRepository categoriaRepository;
    private final PartidoRepository partidoRepository;
    private final ResultadoService resultadoService;

    @Transactional
    public ParejaResponse inscribir(Long torneoId, ParejaRequest request) {

        Torneo torneo = torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        if (!torneo.getEstado().equals(EstadoTorneo.INSCRIPCION)) {
            throw new EstadoInvalidoException("El torneo no esta en periodo de inscripcion");
        }

        Categoria categoria = categoriaRepository.findById(request.getCategoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.getCategoriaId()));

        boolean categoriaActiva = torneo.getCategorias().stream()
                .anyMatch(c -> c.getId().equals(categoria.getId()));
        if (!categoriaActiva) {
            throw new EstadoInvalidoException(
                    "La categoría no está activa en este torneo");
        }

        Integer cupoCategoria = torneo.getCuposPorCategoria() != null
                ? torneo.getCuposPorCategoria().get(categoria.getId())
                : null;
        if (cupoCategoria != null && cupoCategoria > 0) {
            long inscriptasCategoria = parejaRepository.countByTorneoIdAndCategoriaId(torneoId, categoria.getId());
            if (inscriptasCategoria >= cupoCategoria) {
                throw new EstadoInvalidoException(
                    "La categoría '" + categoria.getNombre() + "' alcanzó el cupo máximo de " + cupoCategoria + " parejas");
            }
        } else if (torneo.getCupoMaximoParejas() != null && torneo.getCupoMaximoParejas() > 0) {
            long inscriptasActuales = parejaRepository.countByTorneoId(torneoId);
            if (inscriptasActuales >= torneo.getCupoMaximoParejas()) {
                throw new EstadoInvalidoException(
                    "El torneo alcanzo el cupo maximo de " + torneo.getCupoMaximoParejas() + " parejas");
            }
        }

        Jugador jugador1 = jugadorRepository.findById(request.getJugador1Id())
                .orElseThrow(() -> new ResourceNotFoundException("Jugador 1", request.getJugador1Id()));
        Jugador jugador2 = jugadorRepository.findById(request.getJugador2Id())
                .orElseThrow(() -> new ResourceNotFoundException("Jugador 2", request.getJugador2Id()));

        if (jugador1.getId().equals(jugador2.getId())) {
            throw new EstadoInvalidoException("Un jugador no puede formar pareja consigo mismo");
        }

        validarGeneroPareja(jugador1, jugador2, categoria, torneo.isEsMixto());

        if (parejaRepository.jugadorYaInscriptoEnCategoria(torneoId, categoria.getId(), jugador1.getId())) {
            throw new EstadoInvalidoException("El jugador 1 ya está inscripto en esta categoría");
        }
        if (parejaRepository.jugadorYaInscriptoEnCategoria(torneoId, categoria.getId(), jugador2.getId())) {
            throw new EstadoInvalidoException("El jugador 2 ya está inscripto en esta categoría");
        }

        Pareja pareja = parejaMapper.requestToPareja(request, jugador1, jugador2, categoria, torneo);
        parejaRepository.save(pareja);

        ParejaResponse parejaDTO = parejaMapper.parejaToResponse(pareja);
        return parejaDTO;
    }

    private void validarGeneroPareja(Jugador j1, Jugador j2, Categoria categoria, boolean esMixto) {
        Genero g1 = j1.getGenero();
        Genero g2 = j2.getGenero();

        if (g1 == null || g2 == null) {
            throw new EstadoInvalidoException("Ambos jugadores deben tener un género asignado");
        }

        if (esMixto) {
            boolean esParejaMixta = (g1 == Genero.MASCULINO && g2 == Genero.FEMENINO)
                    || (g1 == Genero.FEMENINO && g2 == Genero.MASCULINO);
            if (!esParejaMixta) {
                throw new EstadoInvalidoException(
                        "En un torneo mixto cada pareja debe estar formada por un jugador masculino y uno femenino");
            }
        } else {
            if (g1 != g2) {
                throw new EstadoInvalidoException(
                        "Ambos jugadores deben ser del mismo género para la categoría '" + categoria.getNombre() + "'");
            }
            if (g1 != categoria.getGenero()) {
                throw new EstadoInvalidoException(
                        "Los jugadores no coinciden con el género de la categoría '" + categoria.getNombre() + "' (" + categoria.getGenero() + ")");
            }
        }
    }

    @Transactional
    public void eliminarDelTorneo(Long torneoId, Long parejaId) {
        Torneo torneo = torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        if (!torneo.getEstado().equals(EstadoTorneo.INSCRIPCION)) {
            throw new EstadoInvalidoException("Solo se puede eliminar parejas en estado INSCRIPCION");
        }

        Pareja pareja = parejaRepository.findById(parejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pareja", parejaId));

        if (!pareja.getTorneo().getId().equals(torneoId)) {
            throw new EstadoInvalidoException("La pareja no pertenece al torneo indicado");
        }

        parejaRepository.delete(pareja);
    }

    @Transactional
    public void retirarPareja(Long torneoId, Long parejaId) {
        Torneo torneo = torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        if (torneo.getEstado().equals(EstadoTorneo.BORRADOR)
                || torneo.getEstado().equals(EstadoTorneo.INSCRIPCION)
                || torneo.getEstado().equals(EstadoTorneo.FINALIZADO)
                || torneo.getEstado().equals(EstadoTorneo.CANCELADO)) {
            throw new EstadoInvalidoException(
                "Solo se puede retirar una pareja en torneos SORTEADOS o EN CURSO");
        }

        Pareja pareja = parejaRepository.findById(parejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pareja", parejaId));

        if (!pareja.getTorneo().getId().equals(torneoId)) {
            throw new EstadoInvalidoException("La pareja no pertenece al torneo indicado");
        }

        List<Partido> partidosPendientes = partidoRepository.findByTorneoId(torneoId).stream()
                .filter(p -> (p.getEstado().equals(EstadoPartido.PENDIENTE)
                           || p.getEstado().equals(EstadoPartido.EN_CURSO))
                        && (esLocal(p, parejaId) || esVisitante(p, parejaId)))
                .toList();

        for (Partido partido : partidosPendientes) {
            Pareja rival = esLocal(partido, parejaId) ? partido.getVisitante() : partido.getLocal();
            if (rival == null) continue;

            partido.setEstado(EstadoPartido.WALKOVER);
            partido.setGanador(rival);
            partidoRepository.save(partido);

            resultadoService.avanzarBracketDespuesDeWO(partido);
        }
    }

    private boolean esLocal(Partido p, Long parejaId) {
        return p.getLocal() != null && p.getLocal().getId().equals(parejaId);
    }

    private boolean esVisitante(Partido p, Long parejaId) {
        return p.getVisitante() != null && p.getVisitante().getId().equals(parejaId);
    }

    @Transactional(readOnly = true)
    public List<ParejaResponse> listarPorTorneo(Long torneoId) {
        torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        return parejaRepository.findByTorneoId(torneoId)
                .stream()
                .map(parejaMapper::parejaToResponse)
                .collect(Collectors.toList());
    }

}
