package com.padel.rankpadel.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.LoteReservaRequest;
import com.padel.rankpadel.dto.request.SolicitudReservaRequest;
import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.dto.response.SlotDisponibilidad;
import com.padel.rankpadel.service.DisponibilidadCanchaService;
import com.padel.rankpadel.service.ReservaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;
    private final DisponibilidadCanchaService disponibilidadCanchaService;

    @GetMapping("/disponibilidad")
    public ResponseEntity<List<SlotDisponibilidad>> disponibilidad(
            @RequestParam Long canchaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(disponibilidadCanchaService.slots(canchaId, fecha));
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> solicitar(@Valid @RequestBody SolicitudReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.solicitar(request));
    }

    @PostMapping("/lote")
    public ResponseEntity<List<ReservaResponse>> solicitarLote(@Valid @RequestBody LoteReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.solicitarLote(request));
    }

    @GetMapping
    public ResponseEntity<List<ReservaResponse>> listar(
            @RequestParam Long canchaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(reservaService.listarPorFecha(canchaId, fecha));
    }

    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<ReservaResponse> confirmar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.confirmar(id));
    }

    @PatchMapping("/{id}/rechazar")
    public ResponseEntity<ReservaResponse> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.rechazar(id));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponse> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.cancelar(id));
    }
}
