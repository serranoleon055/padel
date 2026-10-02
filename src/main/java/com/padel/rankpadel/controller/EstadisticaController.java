package com.padel.rankpadel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.response.EstadisticasResponse;
import com.padel.rankpadel.service.EstadisticaService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/estadisticas")
@RequiredArgsConstructor
public class EstadisticaController {

    private final EstadisticaService estadisticaService;

    @Operation(summary = "Inteligencia de negocio para el panel admin (requiere ADMIN)",
            description = "Con `meses` se elige la ventana que mira el panel, contando el actual. Por defecto 6.")
    @GetMapping
    public ResponseEntity<EstadisticasResponse> obtener(
            @RequestParam(required = false) Long lugarId,
            @RequestParam(required = false) Integer meses) {
        return ResponseEntity.ok(estadisticaService.obtener(lugarId, meses));
    }
}
