package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.CrucePreviewResponse;
import com.padel.rankpadel.entity.Grupo;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.exception.ResourceNotFoundException;
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

    @Transactional(readOnly = true)
    public List<CrucePreviewResponse> previsualizar(Long torneoId, Long categoriaId) {
        Torneo torneo = torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));

        if (!torneo.isIncluyeFaseGrupos() || !torneo.isIncluyeEliminacion()) {
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

        int avanzan = torneo.getAvanzanPorGrupo() != null ? torneo.getAvanzanPorGrupo() : 1;

        List<String> clasificados = new ArrayList<>();
        for (int posicion = 1; posicion <= avanzan; posicion++) {
            for (Grupo grupo : grupos) {
                clasificados.add(posicion + "° " + grupo.getNombre());
            }
        }
        if (clasificados.size() < 2) {
            return List.of();
        }

        int tamano = 1;
        while (tamano < clasificados.size()) {
            tamano *= 2;
        }

        List<String> ranurados = new ArrayList<>();
        for (int i = 0; i < tamano; i++) {
            ranurados.add(i < clasificados.size() ? clasificados.get(i) : null);
        }

        int[] orden = BracketSeeder.ordenDeSiembra(tamano);
        String ronda = nombreRonda(tamano);

        List<CrucePreviewResponse> cruces = new ArrayList<>();
        for (int i = 0; i + 1 < orden.length; i += 2) {
            String a = ranurados.get(orden[i] - 1);
            String b = ranurados.get(orden[i + 1] - 1);
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
