package com.padel.rankpadel.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.SponsorRequest;
import com.padel.rankpadel.dto.response.SponsorResponse;
import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.entity.Sponsor;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.LugarRepository;
import com.padel.rankpadel.repository.SponsorRepository;

import lombok.RequiredArgsConstructor;

/** Auspiciantes del club: quién aparece en las páginas públicas y en qué orden. */
@Service
@RequiredArgsConstructor
public class SponsorService {

    private final SponsorRepository sponsorRepository;
    private final LugarRepository lugarRepository;

    /** Los que ve el público. Un sponsor sin sede se muestra en todas. */
    @Transactional(readOnly = true)
    public List<SponsorResponse> visibles(Long lugarId) {
        return sponsorRepository.findVisibles(lugarId).stream().map(this::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SponsorResponse> listarTodos() {
        return sponsorRepository.findTodos().stream().map(this::aResponse).toList();
    }

    @Transactional
    public SponsorResponse crear(SponsorRequest request) {
        Sponsor sponsor = Sponsor.builder()
                .nombre(request.getNombre().trim())
                .logoUrl(request.getLogoUrl().trim())
                .enlace(normalizarEnlace(request.getEnlace()))
                .lugar(lugar(request.getLugarId()))
                .orden(request.getOrden())
                .activo(request.getActivo() == null || request.getActivo())
                .creadoEn(LocalDateTime.now())
                .build();
        return aResponse(sponsorRepository.save(sponsor));
    }

    @Transactional
    public SponsorResponse actualizar(Long id, SponsorRequest request) {
        Sponsor sponsor = buscar(id);
        sponsor.setNombre(request.getNombre().trim());
        sponsor.setLogoUrl(request.getLogoUrl().trim());
        sponsor.setEnlace(normalizarEnlace(request.getEnlace()));
        sponsor.setLugar(lugar(request.getLugarId()));
        sponsor.setOrden(request.getOrden());
        if (request.getActivo() != null) {
            sponsor.setActivo(request.getActivo());
        }
        return aResponse(sponsorRepository.save(sponsor));
    }

    @Transactional
    public void eliminar(Long id) {
        sponsorRepository.delete(buscar(id));
    }

    /**
     * El club escribe "instagram.com/loquesea" y espera que el logo lleve ahí. Sin
     * esquema, el navegador lo toma como una ruta del propio sitio y no sale a ningún
     * lado.
     */
    private String normalizarEnlace(String enlace) {
        if (enlace == null || enlace.isBlank()) {
            return null;
        }
        String limpio = enlace.trim();
        return limpio.startsWith("http://") || limpio.startsWith("https://") ? limpio : "https://" + limpio;
    }

    private Lugar lugar(Long lugarId) {
        if (lugarId == null) {
            return null;
        }
        return lugarRepository.findById(lugarId)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", lugarId));
    }

    private Sponsor buscar(Long id) {
        return sponsorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sponsor", id));
    }

    private SponsorResponse aResponse(Sponsor sponsor) {
        Lugar lugar = sponsor.getLugar();
        return SponsorResponse.builder()
                .id(sponsor.getId())
                .nombre(sponsor.getNombre())
                .logoUrl(sponsor.getLogoUrl())
                .enlace(sponsor.getEnlace())
                .lugarId(lugar != null ? lugar.getId() : null)
                .lugarNombre(lugar != null ? lugar.getNombre() : null)
                .orden(sponsor.getOrden())
                .activo(sponsor.isActivo())
                .build();
    }
}
