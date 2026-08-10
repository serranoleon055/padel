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

import com.padel.rankpadel.dto.request.TarifaCanchaRequest;
import com.padel.rankpadel.dto.response.TarifaCanchaResponse;
import com.padel.rankpadel.service.TarifaCanchaService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tarifas-cancha")
@RequiredArgsConstructor
@Tag(name = "Tarifas", description = "Precio por franja horaria de cada cancha")
public class TarifaCanchaController {

    private final TarifaCanchaService tarifaCanchaService;

    /** Público: la página de precios muestra las franjas del club. */
    @GetMapping
    public ResponseEntity<List<TarifaCanchaResponse>> listar(@RequestParam Long canchaId) {
        return ResponseEntity.ok(tarifaCanchaService.listar(canchaId));
    }

    @PostMapping
    public ResponseEntity<TarifaCanchaResponse> crear(@Valid @RequestBody TarifaCanchaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tarifaCanchaService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TarifaCanchaResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody TarifaCanchaRequest request) {
        return ResponseEntity.ok(tarifaCanchaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        tarifaCanchaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
