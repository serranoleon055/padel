package com.padel.rankpadel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.AporteSponsorRequest;
import com.padel.rankpadel.dto.request.EntregaPremioRequest;
import com.padel.rankpadel.dto.request.GastoTorneoRequest;
import com.padel.rankpadel.dto.request.PremioTorneoRequest;
import com.padel.rankpadel.dto.response.FinanzasTorneoResponse;
import com.padel.rankpadel.service.FinanzasTorneoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/torneos/{torneoId}/finanzas")
@RequiredArgsConstructor
@Tag(name = "Finanzas del torneo", description = "Premios, gastos, sponsors y balance. Solo el dueño.")
public class FinanzasTorneoController {

    private final FinanzasTorneoService finanzasTorneoService;

    @Operation(summary = "Balance y detalle de la plata del torneo", description = "Requiere JWT de dueño.")
    @GetMapping
    public ResponseEntity<FinanzasTorneoResponse> finanzas(@PathVariable Long torneoId) {
        return ResponseEntity.ok(finanzasTorneoService.finanzas(torneoId));
    }

    @PostMapping("/premios")
    public ResponseEntity<FinanzasTorneoResponse> agregarPremio(@PathVariable Long torneoId,
            @Valid @RequestBody PremioTorneoRequest request) {
        return ResponseEntity.ok(finanzasTorneoService.agregarPremio(torneoId, request));
    }

    @PutMapping("/premios/{premioId}")
    public ResponseEntity<FinanzasTorneoResponse> editarPremio(@PathVariable Long torneoId, @PathVariable Long premioId,
            @Valid @RequestBody PremioTorneoRequest request) {
        return ResponseEntity.ok(finanzasTorneoService.editarPremio(torneoId, premioId, request));
    }

    @PatchMapping("/premios/{premioId}/entrega")
    public ResponseEntity<FinanzasTorneoResponse> entregarPremio(@PathVariable Long torneoId, @PathVariable Long premioId,
            @Valid @RequestBody EntregaPremioRequest request) {
        return ResponseEntity.ok(finanzasTorneoService.entregarPremio(torneoId, premioId, request));
    }

    @DeleteMapping("/premios/{premioId}")
    public ResponseEntity<FinanzasTorneoResponse> quitarPremio(@PathVariable Long torneoId, @PathVariable Long premioId) {
        return ResponseEntity.ok(finanzasTorneoService.quitarPremio(torneoId, premioId));
    }

    @PostMapping("/gastos")
    public ResponseEntity<FinanzasTorneoResponse> registrarGasto(@PathVariable Long torneoId,
            @Valid @RequestBody GastoTorneoRequest request) {
        return ResponseEntity.ok(finanzasTorneoService.registrarGasto(torneoId, request));
    }

    @DeleteMapping("/gastos/{gastoId}")
    public ResponseEntity<FinanzasTorneoResponse> anularGasto(@PathVariable Long torneoId, @PathVariable Long gastoId,
            @RequestParam(required = false) String motivo) {
        return ResponseEntity.ok(finanzasTorneoService.anularGasto(torneoId, gastoId, motivo));
    }

    @PostMapping("/aportes")
    public ResponseEntity<FinanzasTorneoResponse> agregarAporte(@PathVariable Long torneoId,
            @Valid @RequestBody AporteSponsorRequest request) {
        return ResponseEntity.ok(finanzasTorneoService.agregarAporte(torneoId, request));
    }

    @PutMapping("/aportes/{aporteId}")
    public ResponseEntity<FinanzasTorneoResponse> editarAporte(@PathVariable Long torneoId, @PathVariable Long aporteId,
            @Valid @RequestBody AporteSponsorRequest request) {
        return ResponseEntity.ok(finanzasTorneoService.editarAporte(torneoId, aporteId, request));
    }

    @DeleteMapping("/aportes/{aporteId}")
    public ResponseEntity<FinanzasTorneoResponse> quitarAporte(@PathVariable Long torneoId, @PathVariable Long aporteId) {
        return ResponseEntity.ok(finanzasTorneoService.quitarAporte(torneoId, aporteId));
    }
}
