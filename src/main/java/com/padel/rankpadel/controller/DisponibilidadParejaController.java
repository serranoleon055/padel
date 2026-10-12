package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.DisponibilidadParejaRequest;
import com.padel.rankpadel.dto.response.DisponibilidadParejaResponse;
import com.padel.rankpadel.service.DisponibilidadParejaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/torneos/{torneoId}/disponibilidad")
@RequiredArgsConstructor
@Tag(name = "Disponibilidad de parejas", description = "Cuándo no puede jugar cada pareja del torneo")
public class DisponibilidadParejaController {

    private final DisponibilidadParejaService disponibilidadParejaService;

    @Operation(summary = "Horarios en que no pueden jugar las parejas del torneo", description = "Requiere JWT.")
    @GetMapping
    public ResponseEntity<List<DisponibilidadParejaResponse>> listar(@PathVariable Long torneoId) {
        return ResponseEntity.ok(disponibilidadParejaService.listar(torneoId));
    }

    @Operation(summary = "Corregir cuándo no puede jugar una pareja", description = "Requiere JWT. Reemplaza lo que tenía.")
    @PutMapping("/{parejaId}")
    public ResponseEntity<DisponibilidadParejaResponse> actualizar(@PathVariable Long torneoId, @PathVariable Long parejaId,
            @Valid @RequestBody DisponibilidadParejaRequest request) {
        return ResponseEntity.ok(disponibilidadParejaService.actualizar(torneoId, parejaId, request));
    }
}
