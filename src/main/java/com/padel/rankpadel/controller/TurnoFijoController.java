package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.TurnoFijoRequest;
import com.padel.rankpadel.dto.response.DisponibilidadAbonosResponse;
import com.padel.rankpadel.dto.response.GeneracionTurnosFijosResponse;
import com.padel.rankpadel.dto.response.TurnoFijoResponse;
import com.padel.rankpadel.service.TurnoFijoService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/turnos-fijos")
@RequiredArgsConstructor
@Tag(name = "Turnos fijos", description = "Abonos semanales: el mismo cliente, cancha, día y hora todas las semanas")
public class TurnoFijoController {

    private final TurnoFijoService turnoFijoService;

    @GetMapping
    public ResponseEntity<List<TurnoFijoResponse>> listar(
            @RequestParam(required = false) Long lugarId,
            @RequestParam(required = false) Long canchaId) {
        return ResponseEntity.ok(turnoFijoService.listar(lugarId, canchaId));
    }

    /**
     * Qué franjas le quedan al club por vender como abono, por día y por cancha. Es la
     * grilla que el club publica; lo que ocupa acá es otro abono, no un turno suelto.
     */
    @GetMapping("/disponibilidad-semanal")
    public ResponseEntity<DisponibilidadAbonosResponse> disponibilidadSemanal(
            @RequestParam(required = false) Long lugarId) {
        return ResponseEntity.ok(turnoFijoService.disponibilidadSemanal(lugarId));
    }

    @PostMapping
    public ResponseEntity<TurnoFijoResponse> crear(@Valid @RequestBody TurnoFijoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(turnoFijoService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TurnoFijoResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody TurnoFijoRequest request) {
        return ResponseEntity.ok(turnoFijoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Long id) {
        turnoFijoService.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }

    /** Reactiva un abono dado de baja. Después hay que generar sus turnos con /generar. */
    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<TurnoFijoResponse> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(turnoFijoService.reactivar(id));
    }

    /** Genera ya las reservas de este abono, sin esperar al scheduler diario. */
    @PostMapping("/{id}/generar")
    public ResponseEntity<GeneracionTurnosFijosResponse> generar(@PathVariable Long id) {
        return ResponseEntity.ok(turnoFijoService.generarUno(id));
    }

    @PostMapping("/generar")
    public ResponseEntity<GeneracionTurnosFijosResponse> generarTodos() {
        return ResponseEntity.ok(turnoFijoService.generarTodos());
    }
}
