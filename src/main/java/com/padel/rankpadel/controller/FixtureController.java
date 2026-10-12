package com.padel.rankpadel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.AplicarFixtureRequest;
import com.padel.rankpadel.dto.request.MoverPartidoRequest;
import com.padel.rankpadel.dto.request.PropuestaFixtureRequest;
import com.padel.rankpadel.dto.request.ReglasFixtureRequest;
import com.padel.rankpadel.dto.response.FixtureResponse;
import com.padel.rankpadel.dto.response.PropuestaFixtureResponse;
import com.padel.rankpadel.dto.response.ReglasFixtureResponse;
import com.padel.rankpadel.service.FixtureService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/torneos/{torneoId}/fixture")
@RequiredArgsConstructor
@Tag(name = "Fixture", description = "Horarios del torneo: armado automático, ajuste a mano y conflictos")
public class FixtureController {

    private final FixtureService fixtureService;

    @Operation(summary = "Horarios del torneo con sus conflictos", description = "Requiere JWT.")
    @GetMapping
    public ResponseEntity<FixtureResponse> agenda(@PathVariable Long torneoId) {
        return ResponseEntity.ok(fixtureService.agenda(torneoId));
    }

    @GetMapping("/reglas")
    public ResponseEntity<ReglasFixtureResponse> reglas(@PathVariable Long torneoId) {
        return ResponseEntity.ok(fixtureService.reglas(torneoId));
    }

    @PutMapping("/reglas")
    public ResponseEntity<ReglasFixtureResponse> guardarReglas(@PathVariable Long torneoId,
            @Valid @RequestBody ReglasFixtureRequest request) {
        return ResponseEntity.ok(fixtureService.guardarReglas(torneoId, request));
    }

    @Operation(summary = "Proponer horarios", description = "Requiere JWT. No guarda nada: devuelve la propuesta para revisar.")
    @PostMapping("/propuesta")
    public ResponseEntity<PropuestaFixtureResponse> proponer(@PathVariable Long torneoId,
            @RequestBody PropuestaFixtureRequest request) {
        return ResponseEntity.ok(fixtureService.proponer(torneoId, request));
    }

    @Operation(summary = "Aplicar una propuesta", description = "Requiere JWT. Se revisa de nuevo antes de guardar.")
    @PostMapping("/aplicar")
    public ResponseEntity<FixtureResponse> aplicar(@PathVariable Long torneoId,
            @Valid @RequestBody AplicarFixtureRequest request) {
        return ResponseEntity.ok(fixtureService.aplicar(torneoId, request));
    }

    @Operation(summary = "Mover un partido", description = "Requiere JWT. Rechaza choques de cancha o de jugador; lo dudoso queda como conflicto.")
    @PutMapping("/partidos/{partidoId}")
    public ResponseEntity<FixtureResponse> mover(@PathVariable Long torneoId, @PathVariable Long partidoId,
            @Valid @RequestBody MoverPartidoRequest request) {
        return ResponseEntity.ok(fixtureService.mover(torneoId, partidoId, request));
    }

    @DeleteMapping("/partidos/{partidoId}")
    public ResponseEntity<FixtureResponse> desprogramar(@PathVariable Long torneoId, @PathVariable Long partidoId) {
        return ResponseEntity.ok(fixtureService.desprogramar(torneoId, partidoId));
    }
}
