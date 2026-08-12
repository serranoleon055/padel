package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.SponsorRequest;
import com.padel.rankpadel.dto.response.SponsorResponse;
import com.padel.rankpadel.service.SponsorService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sponsors")
@RequiredArgsConstructor
@Tag(name = "Sponsors", description = "Auspiciantes del club en las páginas públicas")
public class SponsorController {

    private final SponsorService sponsorService;

    /** Público: la franja que ve el jugador. Solo devuelve los activos. */
    @GetMapping
    public ResponseEntity<List<SponsorResponse>> visibles(@RequestParam(required = false) Long lugarId) {
        return ResponseEntity.ok(sponsorService.visibles(lugarId));
    }

    /** Panel: incluye los dados de baja, para poder reactivarlos. */
    @GetMapping("/todos")
    public ResponseEntity<List<SponsorResponse>> listarTodos() {
        return ResponseEntity.ok(sponsorService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<SponsorResponse> crear(@Valid @RequestBody SponsorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sponsorService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SponsorResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody SponsorRequest request) {
        return ResponseEntity.ok(sponsorService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        sponsorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
