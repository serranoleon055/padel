package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.CrucePreviewResponse;
import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;
import com.padel.rankpadel.entity.Grupo;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.ConfiguracionCategoriaTorneoRepository;
import com.padel.rankpadel.repository.GrupoRepository;
import com.padel.rankpadel.repository.RondaEliminatoriasRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.util.BracketSeeder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CuadroPreviewService {

    private final TorneoRepository torneoRepository;
    private final GrupoRepository grupoRepository;
    private final RondaEliminatoriasRepository rondaEliminatoriasRepository;
    private final ConfiguracionCategoriaTorneoRepository configuracionCategoriaTorneoRepository;

    @Transactional(readOnly = true)
    public List<CrucePreviewResponse> previsualizar(Long torneoId, Long categoriaId) {
        Torneo torneo = torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        ConfiguracionCategoriaTorneo config = configuracionCategoriaTorneoRepository
                .findByTorneoIdAndCategoriaId(torneoId, categoriaId).orElse(null);

        boolean incluyeFaseGrupos = config != null ? config.isIncluyeFaseGrupos() : torneo.isIncluyeFaseGrupos();
        boolean incluyeEliminacion = config != null ? config.isIncluyeEliminacion() : torneo.isIncluyeEliminacion();
        if (!incluyeFaseGrupos || !incluyeEliminacion) {
            return List.of();
        }

        if (!rondaEliminatoriasRepository.findByTorneoIdAndCategoriaIdOrderByOrden(torneoId, categoriaId).isEmpty()) {
            return List.of();
        }

        List<Grupo> grupos = grupoRepository.findByTorneoIdAndCategoriaId(torneoId, categoriaId).stream()
                .sorted(Comparator.comparing(Grupo::getNombre, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (grupos.isEmpty()) {
            return List.of();
        }

        Integer avanzanConfig = config != null ? config.getAvanzanPorGrupo() : torneo.getAvanzanPorGrupo();
        int avanzan = avanzanConfig != null ? avanzanConfig : 1;

        List<String> clasificados = new ArrayList<>();
        List<Long> grupoPorClasificado = new ArrayList<>();
        for (int posicion = 1; posicion <= avanzan; posicion++) {
            for (Grupo grupo : grupos) {
                clasificados.add(posicion + "° " + grupo.getNombre());
                grupoPorClasificado.add(grupo.getId() != null ? grupo.getId() : 0L);
            }
        }
        if (clasificados.size() < 2) {
            return List.of();
        }

        int tamano = 1;
        while (tamano < clasificados.size()) {
            tamano *= 2;
        }
        String ronda = nombreRonda(tamano);

        long[] grupoIds = new long[grupoPorClasificado.size()];
        for (int i = 0; i < grupoIds.length; i++) {
            grupoIds[i] = grupoPorClasificado.get(i);
        }

        List<CrucePreviewResponse> cruces = new ArrayList<>();
        for (int[] par : BracketSeeder.emparejarIndices(clasificados.size(), grupoIds)) {
            String a = par[0] >= 0 ? clasificados.get(par[0]) : null;
            String b = par[1] >= 0 ? clasificados.get(par[1]) : null;
            String local = a != null ? a : b;
            String visitante = a != null ? b : null;
            cruces.add(CrucePreviewResponse.builder()
                    .ronda(ronda)
                    .local(local)
                    .visitante(visitante != null ? visitante : "BYE (pasa directo)")
                    .build());
        }
        return cruces;
    }

    private String nombreRonda(int tamano) {
        return switch (tamano) {
            case 16 -> "Octavos de final";
            case 8 -> "Cuartos de final";
            case 4 -> "Semifinales";
            case 2 -> "Final";
            default -> "Eliminación";
        };
    }
}
