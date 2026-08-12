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

import com.padel.rankpadel.dto.request.PromocionCanchaRequest;
import com.padel.rankpadel.dto.response.PromocionCanchaResponse;
import com.padel.rankpadel.service.PromocionCanchaService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/promociones-cancha")
@RequiredArgsConstructor
@Tag(name = "Promociones", description = "Precios especiales por días, horario y vigencia")
public class PromocionCanchaController {

    private final PromocionCanchaService promocionCanchaService;

    /** Público: la página de precios muestra las promociones vigentes del club. */
    @GetMapping
    public ResponseEntity<List<PromocionCanchaResponse>> listar(@RequestParam Long canchaId) {
        return ResponseEntity.ok(promocionCanchaService.listar(canchaId));
    }

    @PostMapping
    public ResponseEntity<PromocionCanchaResponse> crear(@Valid @RequestBody PromocionCanchaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promocionCanchaService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromocionCanchaResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody PromocionCanchaRequest request) {
        return ResponseEntity.ok(promocionCanchaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        promocionCanchaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
