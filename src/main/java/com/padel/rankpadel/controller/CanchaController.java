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

import com.padel.rankpadel.dto.request.CanchaRequest;
import com.padel.rankpadel.dto.response.CanchaResponse;
import com.padel.rankpadel.service.CanchaService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/canchas")
@Tag(name = "Canchas", description = "Gestión de canchas (pistas) dentro de un lugar/club.")
public class CanchaController {

    private final CanchaService canchaService;

    @SecurityRequirements({})
    @GetMapping
    public ResponseEntity<List<CanchaResponse>> listar(
            @RequestParam(required = false) Long lugarId) {
        List<CanchaResponse> result = lugarId != null
                ? canchaService.listarPorLugar(lugarId)
                : canchaService.listarTodas();
        return ResponseEntity.ok(result);
    }

    @SecurityRequirements({})
    @GetMapping("/{id}")
    public ResponseEntity<CanchaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(canchaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CanchaResponse> crear(@Valid @RequestBody CanchaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canchaService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CanchaResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody CanchaRequest request) {
        return ResponseEntity.ok(canchaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        canchaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

}
