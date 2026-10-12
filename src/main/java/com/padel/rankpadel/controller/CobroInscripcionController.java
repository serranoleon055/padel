package com.padel.rankpadel.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.PagoJugadorRequest;
import com.padel.rankpadel.dto.response.CuentaInscripcionesResponse;
import com.padel.rankpadel.service.CobroInscripcionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/torneos/{torneoId}/cobros-inscripcion")
@RequiredArgsConstructor
@Tag(name = "Cobro de inscripciones", description = "Quién pagó la inscripción y quién debe, jugador por jugador")
public class CobroInscripcionController {

    private final CobroInscripcionService cobroInscripcionService;

    @Operation(summary = "Cuenta de inscripciones del torneo", description = "Requiere JWT.")
    @GetMapping
    public ResponseEntity<CuentaInscripcionesResponse> cuenta(@PathVariable Long torneoId) {
        return ResponseEntity.ok(cobroInscripcionService.cuenta(torneoId));
    }

    @Operation(summary = "Cobrarle a un jugador", description = "Requiere JWT. Acepta pagos parciales; no más que lo que debe.")
    @PostMapping("/parejas/{parejaId}")
    public ResponseEntity<CuentaInscripcionesResponse> cobrar(@PathVariable Long torneoId, @PathVariable Long parejaId,
            @Valid @RequestBody PagoJugadorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cobroInscripcionService.registrar(torneoId, parejaId, request));
    }

    @Operation(summary = "Anular un pago de inscripción", description = "Requiere JWT de dueño.")
    @DeleteMapping("/{pagoId}")
    public ResponseEntity<CuentaInscripcionesResponse> anular(@PathVariable Long torneoId, @PathVariable Long pagoId,
            @RequestParam(required = false) String motivo) {
        return ResponseEntity.ok(cobroInscripcionService.anular(torneoId, pagoId, motivo));
    }
}
