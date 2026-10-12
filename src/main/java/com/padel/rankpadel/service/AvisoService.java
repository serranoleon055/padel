package com.padel.rankpadel.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.AvisoEnviadoRequest;
import com.padel.rankpadel.dto.response.AvisoEnviadoResponse;
import com.padel.rankpadel.entity.AvisoEnviado;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.AvisoEnviadoRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * A quién ya se le avisó por WhatsApp y con qué mensaje. Hay un registro por jugador y
 * tema: volver a avisar lo pisa con la huella del mensaje nuevo.
 */
@Service
@RequiredArgsConstructor
public class AvisoService {

    private final AvisoEnviadoRepository avisoEnviadoRepository;
    private final TorneoRepository torneoRepository;

    @Transactional(readOnly = true)
    public List<AvisoEnviadoResponse> listar(Long torneoId) {
        exigirTorneo(torneoId);
        return avisoEnviadoRepository.findByTorneoId(torneoId).stream().map(AvisoService::aResponse).toList();
    }

    @Transactional
    public AvisoEnviadoResponse registrar(Long torneoId, AvisoEnviadoRequest request) {
        exigirTorneo(torneoId);
        AvisoEnviado aviso = avisoEnviadoRepository
                .findByTorneoIdAndJugadorIdAndTema(torneoId, request.getJugadorId(), request.getTema())
                .orElseGet(() -> AvisoEnviado.builder().torneoId(torneoId).jugadorId(request.getJugadorId())
                        .tema(request.getTema()).build());
        aviso.setClave(request.getClave());
        aviso.setEnviadoEn(LocalDateTime.now());
        aviso.setEnviadoPor(UsuarioActual.nombre());
        return aResponse(avisoEnviadoRepository.save(aviso));
    }

    private void exigirTorneo(Long torneoId) {
        if (!torneoRepository.existsById(torneoId)) {
            throw new ResourceNotFoundException("Torneo", torneoId);
        }
    }

    private static AvisoEnviadoResponse aResponse(AvisoEnviado aviso) {
        return AvisoEnviadoResponse.builder()
                .jugadorId(aviso.getJugadorId())
                .tema(aviso.getTema())
                .clave(aviso.getClave())
                .enviadoEn(aviso.getEnviadoEn())
                .enviadoPor(aviso.getEnviadoPor())
                .build();
    }
}
