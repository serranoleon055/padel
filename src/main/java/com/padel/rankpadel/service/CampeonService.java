package com.padel.rankpadel.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.CampeonResponse;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.CampeonTorneo;
import com.padel.rankpadel.entity.Grupo;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.PosicionGrupo;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.repository.CampeonTorneoRepository;
import com.padel.rankpadel.repository.GrupoRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.PosicionGrupoRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.util.PosicionGrupoOrdenador;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Determina y persiste el campeón/subcampeón de cada categoría de un torneo,
 * sirviendo de fuente única tanto para torneos por eliminación (ganador de la
 * final) como para ligas/grupos (líder de la tabla de posiciones).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CampeonService {

    private final CampeonTorneoRepository campeonTorneoRepository;
    private final GrupoRepository grupoRepository;
    private final PosicionGrupoRepository posicionGrupoRepository;
    private final PartidoRepository partidoRepository;
    private final TorneoRepository torneoRepository;

    /** Recalcula los campeones de todas las categorías de un torneo. */
    public void recalcularCampeones(Torneo torneo) {
        campeonTorneoRepository.deleteByTorneoId(torneo.getId());
        if (torneo.getCategorias() == null) {
            return;
        }
        List<CampeonTorneo> nuevos = new ArrayList<>();
        for (Categoria categoria : torneo.getCategorias()) {
            CampeonTorneo campeon = computarCampeon(torneo, categoria);
            if (campeon != null) {
                nuevos.add(campeon);
            }
        }
        campeonTorneoRepository.saveAll(nuevos);
    }

    public void eliminarPorTorneo(Long torneoId) {
        campeonTorneoRepository.deleteByTorneoId(torneoId);
    }

    /** Backfill: recalcula los campeones de todos los torneos finalizados activos. */
    public int recalcularTodosFinalizados() {
        List<Torneo> finalizados = torneoRepository.findByActivoTrue().stream()
                .filter(t -> EstadoTorneo.FINALIZADO.equals(t.getEstado()))
                .toList();
        for (Torneo torneo : finalizados) {
            recalcularCampeones(torneo);
        }
        return finalizados.size();
    }

    public PagedResponse<CampeonResponse> listar(Long categoriaId, Genero genero, int pagina, int tamanio) {
        List<CampeonResponse> campeones = campeonTorneoRepository.findAllOrdenados().stream()
                .map(this::mapear)
                .filter(c -> categoriaId == null || categoriaId.equals(c.getCategoriaId()))
                .filter(c -> genero == null || genero.equals(c.getGenero()))
                .toList();
        return PagedResponse.of(campeones, pagina, tamanio);
    }

    public List<CampeonResponse> ultimos(int cantidad) {
        return campeonTorneoRepository.findAllOrdenados().stream()
                .limit(cantidad)
                .map(this::mapear)
                .toList();
    }

    public List<CampeonResponse> porTorneo(Long torneoId) {
        return campeonTorneoRepository.findByTorneoId(torneoId).stream()
                .map(this::mapear)
                .toList();
    }

    private CampeonTorneo computarCampeon(Torneo torneo, Categoria categoria) {
        if (torneo.isIncluyeEliminacion()) {
            return computarDesdeFinal(torneo, categoria);
        }
        return computarDesdeTabla(torneo, categoria);
    }

    private CampeonTorneo computarDesdeFinal(Torneo torneo, Categoria categoria) {
        Partido finalPartido = partidoRepository.findByTorneoId(torneo.getId()).stream()
                .filter(p -> p.getFase() == FasePartido.ELIMINACION)
                .filter(p -> p.getEstado() == EstadoPartido.FINALIZADO && p.getGanador() != null)
                .filter(p -> p.getRonda() != null && "final".equalsIgnoreCase(p.getRonda().getNombre()))
                .filter(p -> p.getRonda().getCategoria() != null
                        && categoria.getId().equals(p.getRonda().getCategoria().getId()))
                .findFirst()
                .orElse(null);

        if (finalPartido == null) {
            return null;
        }

        Pareja campeona = finalPartido.getGanador();
        Pareja subcampeona = campeona.getId().equals(finalPartido.getLocal().getId())
                ? finalPartido.getVisitante()
                : finalPartido.getLocal();

        return CampeonTorneo.builder()
                .torneo(torneo)
                .categoria(categoria)
                .parejaCampeona(campeona)
                .parejaSubcampeona(subcampeona)
                .marcadorFinal(finalPartido.getMarcador())
                .fechaCoronacion(finalPartido.getFechaHora() != null ? finalPartido.getFechaHora() : LocalDateTime.now())
                .build();
    }

    private CampeonTorneo computarDesdeTabla(Torneo torneo, Categoria categoria) {
        List<Grupo> grupos = grupoRepository.findByTorneoIdAndCategoriaId(torneo.getId(), categoria.getId());
        List<PosicionGrupo> posiciones = new ArrayList<>();
        for (Grupo grupo : grupos) {
            posiciones.addAll(posicionGrupoRepository.findByGrupoId(grupo.getId()));
        }

        boolean algunaJugada = posiciones.stream().anyMatch(p -> p.getPj() > 0);
        if (!algunaJugada) {
            return null;
        }

        List<PosicionGrupo> ordenadas = PosicionGrupoOrdenador.ordenar(posiciones);
        Pareja campeona = ordenadas.get(0).getPareja();
        Pareja subcampeona = ordenadas.size() > 1 ? ordenadas.get(1).getPareja() : null;

        return CampeonTorneo.builder()
                .torneo(torneo)
                .categoria(categoria)
                .parejaCampeona(campeona)
                .parejaSubcampeona(subcampeona)
                .marcadorFinal(null)
                .fechaCoronacion(LocalDateTime.now())
                .build();
    }

    private CampeonResponse mapear(CampeonTorneo campeon) {
        Torneo torneo = campeon.getTorneo();
        Categoria categoria = campeon.getCategoria();
        return CampeonResponse.builder()
                .torneoId(torneo != null ? torneo.getId() : null)
                .torneoNombre(torneo != null ? torneo.getNombre() : null)
                .categoriaId(categoria != null ? categoria.getId() : null)
                .categoriaNombre(categoria != null ? categoria.getNombre() : null)
                .genero(categoria != null ? categoria.getGenero() : null)
                .campeonaId(campeon.getParejaCampeona() != null ? campeon.getParejaCampeona().getId() : null)
                .campeonaNombre(formatearPareja(campeon.getParejaCampeona()))
                .subcampeonaNombre(formatearPareja(campeon.getParejaSubcampeona()))
                .marcadorFinal(campeon.getMarcadorFinal())
                .fecha(campeon.getFechaCoronacion())
                .lugarNombre(torneo != null && torneo.getLugar() != null ? torneo.getLugar().getNombre() : null)
                .build();
    }

    private String formatearPareja(Pareja pareja) {
        if (pareja == null) {
            return null;
        }
        try {
            return pareja.getJugador1().getNombre() + " " + pareja.getJugador1().getApellido()
                    + " / "
                    + pareja.getJugador2().getNombre() + " " + pareja.getJugador2().getApellido();
        } catch (EntityNotFoundException e) {
            return null;
        }
    }
}
