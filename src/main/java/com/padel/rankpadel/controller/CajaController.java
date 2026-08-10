package com.padel.rankpadel.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.padel.rankpadel.dto.request.CobroRequest;
import com.padel.rankpadel.dto.response.CierreCajaResponse;
import com.padel.rankpadel.dto.response.CobroResponse;
import com.padel.rankpadel.service.CajaService;
import com.padel.rankpadel.service.CobroService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Caja", description = "Cobros en el mostrador y cierre de caja diario")
public class CajaController {

    private final CobroService cobroService;
    private final CajaService cajaService;

    @PostMapping("/reservas/{reservaId}/cobros")
    public ResponseEntity<CobroResponse> registrarCobro(@PathVariable Long reservaId,
            @Valid @RequestBody CobroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cobroService.registrar(reservaId, request));
    }

    @GetMapping("/reservas/{reservaId}/cobros")
    public ResponseEntity<java.util.List<CobroResponse>> listarCobros(@PathVariable Long reservaId) {
        return ResponseEntity.ok(cobroService.listarDeReserva(reservaId));
    }

    @DeleteMapping("/cobros/{id}")
    public ResponseEntity<Void> anularCobro(@PathVariable Long id) {
        cobroService.anular(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/caja")
    public ResponseEntity<CierreCajaResponse> cierre(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(cajaService.cierre(fecha != null ? fecha : LocalDate.now()));
    }
}
