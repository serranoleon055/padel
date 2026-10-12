package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.AvisoEnviadoRequest;
import com.padel.rankpadel.dto.response.AvisoEnviadoResponse;
import com.padel.rankpadel.service.AvisoService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/torneos/{torneoId}/avisos")
@RequiredArgsConstructor
@Tag(name = "Avisos", description = "A quién ya se le avisó por WhatsApp")
public class AvisoController {

    private final AvisoService avisoService;

    @GetMapping
    public ResponseEntity<List<AvisoEnviadoResponse>> listar(@PathVariable Long torneoId) {
        return ResponseEntity.ok(avisoService.listar(torneoId));
    }

    @PostMapping
    public ResponseEntity<AvisoEnviadoResponse> registrar(@PathVariable Long torneoId,
            @Valid @RequestBody AvisoEnviadoRequest request) {
        return ResponseEntity.ok(avisoService.registrar(torneoId, request));
    }
}
