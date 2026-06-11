package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.AprobarInscripcionRequest;
import com.padel.rankpadel.dto.request.SolicitudInscripcionRequest;
import com.padel.rankpadel.dto.response.SolicitudInscripcionResponse;
import com.padel.rankpadel.service.InscripcionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class InscripcionController {

    private final InscripcionService inscripcionService;

    @PostMapping("/api/torneos/{torneoId}/inscripciones")
    public ResponseEntity<SolicitudInscripcionResponse> crear(
            @PathVariable Long torneoId,
            @Valid @RequestBody SolicitudInscripcionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inscripcionService.crear(torneoId, request));
    }

    @GetMapping("/api/inscripciones")
    public ResponseEntity<List<SolicitudInscripcionResponse>> listar(
            @RequestParam Long torneoId,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(inscripcionService.listar(torneoId, estado));
    }

    @PatchMapping("/api/inscripciones/{id}/aprobar")
    public ResponseEntity<SolicitudInscripcionResponse> aprobar(
            @PathVariable Long id,
            @RequestBody(required = false) AprobarInscripcionRequest seleccion) {
        return ResponseEntity.ok(inscripcionService.aprobar(id, seleccion));
    }

    @PatchMapping("/api/inscripciones/{id}/rechazar")
    public ResponseEntity<SolicitudInscripcionResponse> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(inscripcionService.rechazar(id));
    }
}
