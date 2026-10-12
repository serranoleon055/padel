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

import com.padel.rankpadel.dto.request.PagoSedeRequest;
import com.padel.rankpadel.dto.request.TorneoSedeRequest;
import com.padel.rankpadel.dto.response.TorneoSedeResponse;
import com.padel.rankpadel.service.TorneoSedeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/torneos/{torneoId}/sedes")
@RequiredArgsConstructor
@Tag(name = "Clubes del torneo", description = "Dónde se juega un torneo, qué canchas cede cada club y cuánto se le paga")
public class TorneoSedeController {

    private final TorneoSedeService torneoSedeService;

    @Operation(summary = "Clubes del torneo", description = "Requiere JWT. Montos y pagos solo para el dueño.")
    @GetMapping
    public ResponseEntity<List<TorneoSedeResponse>> listar(@PathVariable Long torneoId) {
        return ResponseEntity.ok(torneoSedeService.listar(torneoId));
    }

    @PostMapping
    public ResponseEntity<TorneoSedeResponse> agregar(@PathVariable Long torneoId,
            @Valid @RequestBody TorneoSedeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(torneoSedeService.agregar(torneoId, request));
    }

    @PutMapping("/{sedeId}")
    public ResponseEntity<TorneoSedeResponse> actualizar(@PathVariable Long torneoId, @PathVariable Long sedeId,
            @Valid @RequestBody TorneoSedeRequest request) {
        return ResponseEntity.ok(torneoSedeService.actualizar(torneoId, sedeId, request));
    }

    @DeleteMapping("/{sedeId}")
    public ResponseEntity<Void> quitar(@PathVariable Long torneoId, @PathVariable Long sedeId) {
        torneoSedeService.quitar(torneoId, sedeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Pagarle al club", description = "Requiere JWT de dueño. No se puede pagar más que el saldo.")
    @PostMapping("/{sedeId}/pagos")
    public ResponseEntity<TorneoSedeResponse> pagar(@PathVariable Long torneoId, @PathVariable Long sedeId,
            @Valid @RequestBody PagoSedeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(torneoSedeService.registrarPago(torneoId, sedeId, request));
    }

    @Operation(summary = "Anular un pago al club", description = "Requiere JWT de dueño. Queda registrado quién y por qué.")
    @DeleteMapping("/{sedeId}/pagos/{pagoId}")
    public ResponseEntity<TorneoSedeResponse> anularPago(@PathVariable Long torneoId, @PathVariable Long sedeId,
            @PathVariable Long pagoId, @RequestParam(required = false) String motivo) {
        return ResponseEntity.ok(torneoSedeService.anularPago(torneoId, sedeId, pagoId, motivo));
    }
}
