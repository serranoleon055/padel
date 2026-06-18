package com.padel.rankpadel.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.request.TorneoRequest;
import com.padel.rankpadel.dto.response.CategoriaResponse;
import com.padel.rankpadel.dto.response.ConfiguracionPuntosResponse;
import com.padel.rankpadel.dto.response.TorneoResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.entity.Torneo;

@Component
public class TorneoMapper {

    public Torneo requestToTorneo(TorneoRequest torneoRequest, Temporada temporada, Lugar lugar) {

        Torneo torneo = Torneo.builder()
                .nombre(torneoRequest.getNombre())
                .descripcion(torneoRequest.getDescripcion())
                .imagenUrl(torneoRequest.getImagenUrl())
                .cupoMaximoParejas(torneoRequest.getCupoMaximoParejas())
                .costoInscripcionJugador(torneoRequest.getCostoInscripcionJugador())
                .premioAcumulado(torneoRequest.getPremioAcumulado())
                .seniaPorcentaje(torneoRequest.getSeniaPorcentaje())
                .cuposPorCategoria(torneoRequest.getCuposPorCategoria() != null
                        ? new java.util.HashMap<>(torneoRequest.getCuposPorCategoria())
                        : new java.util.HashMap<>())
                .formato(torneoRequest.getFormato())
                .fechaInicio(torneoRequest.getFechaInicio())
                .fechaFin(torneoRequest.getFechaFin())
                .sumaPuntosRanking(torneoRequest.isSumaPuntosRanking())
                .plantillaFormatoId(torneoRequest.getPlantillaFormatoId())
                .plantillaPuntosId(torneoRequest.getPlantillaPuntosId())
                .cantidadParejasObjetivo(torneoRequest.getCantidadParejasObjetivo())
                .cantidadGrupos(torneoRequest.getCantidadGrupos())
                .parejasPorGrupo(torneoRequest.getParejasPorGrupo())
                .avanzanPorGrupo(torneoRequest.getAvanzanPorGrupo())
                .incluyeFaseGrupos(torneoRequest.isIncluyeFaseGrupos())
                .incluyeEliminacion(torneoRequest.isIncluyeEliminacion())
                .mejorDeSets(torneoRequest.getMejorDeSets() != null ? torneoRequest.getMejorDeSets() : 3)
                .tipoSorteo(torneoRequest.getTipoSorteo())
                .temporada(temporada)
                .lugar(lugar)
                .build();

        return torneo;
    }

    public TorneoResponse torneoToResponse(Torneo torneo) {

        TorneoResponse torneoDTO = TorneoResponse.builder()
                .id(torneo.getId())
                .nombre(torneo.getNombre())
                .descripcion(torneo.getDescripcion())
                .imagenUrl(torneo.getImagenUrl())
                .cupoMaximoParejas(torneo.getCupoMaximoParejas())
                .costoInscripcionJugador(torneo.getCostoInscripcionJugador())
                .premioAcumulado(torneo.getPremioAcumulado())
                .seniaPorcentaje(torneo.getSeniaPorcentaje())
                .cuposPorCategoria(torneo.getCuposPorCategoria() != null
                        ? new java.util.HashMap<>(torneo.getCuposPorCategoria())
                        : new java.util.HashMap<>())
                .formato(torneo.getFormato())
                .estado(torneo.getEstado())
                .fechaInicio(torneo.getFechaInicio())
                .fechaFin(torneo.getFechaFin())
                .sumaPuntosRanking(torneo.isSumaPuntosRanking())
                .plantillaFormatoId(torneo.getPlantillaFormatoId())
                .plantillaFormatoNombre(torneo.getPlantillaFormatoNombre())
                .plantillaPuntosId(torneo.getPlantillaPuntosId())
                .plantillaPuntosNombre(torneo.getPlantillaPuntosNombre())
                .cantidadParejasObjetivo(torneo.getCantidadParejasObjetivo())
                .cantidadGrupos(torneo.getCantidadGrupos())
                .parejasPorGrupo(torneo.getParejasPorGrupo())
                .avanzanPorGrupo(torneo.getAvanzanPorGrupo())
                .incluyeFaseGrupos(torneo.isIncluyeFaseGrupos())
                .incluyeEliminacion(torneo.isIncluyeEliminacion())
                .mejorDeSets(torneo.getMejorDeSets())
                .tipoSorteo(torneo.getTipoSorteo())
                .temporadaId(getTemporadaId(torneo))
                .temporadaNombre(getTemporadaNombre(torneo))
                .lugarId(getLugarId(torneo))
                .lugarNombre(getLugarNombre(torneo))
                .categorias(mapearCategorias(torneo))
                .cantidadCategorias(torneo.getCategorias() != null ? torneo.getCategorias().size() : 0)
                .configuracionPuntos(mapearConfiguracionPuntos(torneo))
                .build();

        return torneoDTO;
    }

    private Long getTemporadaId(Torneo torneo) {
        try {
            return torneo.getTemporada() != null ? torneo.getTemporada().getId() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private String getTemporadaNombre(Torneo torneo) {
        try {
            return torneo.getTemporada() != null ? torneo.getTemporada().getNombre() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private Long getLugarId(Torneo torneo) {
        try {
            return torneo.getLugar() != null ? torneo.getLugar().getId() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private String getLugarNombre(Torneo torneo) {
        try {
            return torneo.getLugar() != null ? torneo.getLugar().getNombre() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private List<CategoriaResponse> mapearCategorias(Torneo torneo) {
        if (torneo.getCategorias() == null) {
            return List.of();
        }

        return torneo.getCategorias().stream()
                .map(this::categoriaToResponse)
                .toList();
    }

    private CategoriaResponse categoriaToResponse(Categoria categoria) {
        return CategoriaResponse.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .nivel(categoria.getNivel())
                .genero(categoria.getGenero())
                .build();
    }

    private List<ConfiguracionPuntosResponse> mapearConfiguracionPuntos(Torneo torneo) {
        if (torneo.getConfiguracionPuntos() == null) {
            return List.of();
        }
        return torneo.getConfiguracionPuntos().stream()
                .map(cp -> ConfiguracionPuntosResponse.builder()
                        .nombreRonda(cp.getNombreRonda())
                        .puntosGanador(cp.getPuntosGanador())
                        .puntosPerdedor(cp.getPuntosPerdedor())
                        .orden(cp.getOrden())
                        .build())
                .toList();
    }

}
