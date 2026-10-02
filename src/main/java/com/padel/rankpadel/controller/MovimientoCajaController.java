package com.padel.rankpadel.controller;

import java.time.LocalDate;
import java.util.List;

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

import com.padel.rankpadel.dto.request.MovimientoCajaRequest;
import com.padel.rankpadel.dto.response.MovimientoSueltoResponse;
import com.padel.rankpadel.service.CajaService;
import com.padel.rankpadel.service.MovimientoCajaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * La plata que entra o sale del cajón y no es un turno ni una venta del kiosco.
 *
 * <p>Qué conceptos puede registrar cada rol se decide en el servicio y no acá: depende del
 * cuerpo del pedido, no de la ruta, así que {@code SecurityConfig} no lo puede ver.
 */
@RestController
@RequestMapping("/api/movimientos-caja")
@RequiredArgsConstructor
@Tag(name = "Movimientos de caja", description = "Apertura del día y plata que no es un turno ni una venta")
public class MovimientoCajaController {

    private final MovimientoCajaService movimientoCajaService;
    private final CajaService cajaService;

    @Operation(summary = "Abrir la caja del día",
            description = "Requiere JWT. Declara el efectivo con el que arranca el cajón. Una vez por jornada.")
    @PostMapping("/apertura")
    public ResponseEntity<MovimientoSueltoResponse> abrir(
            @Valid @RequestBody MovimientoCajaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoCajaService.abrir(request));
    }

    @Operation(summary = "Registrar un movimiento de caja",
            description = "Requiere JWT. Los conceptos que sirven para tapar un faltante (retiro del dueño, depósito al banco, préstamo, ajuste) los tiene que cargar el dueño.")
    @PostMapping
    public ResponseEntity<MovimientoSueltoResponse> registrar(
            @Valid @RequestBody MovimientoCajaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoCajaService.registrar(request));
    }

    @Operation(summary = "Movimientos de una jornada",
            description = "Requiere JWT. Sin fecha devuelve la jornada que el club está atendiendo.")
    @GetMapping
    public ResponseEntity<List<MovimientoSueltoResponse>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jornada) {
        return ResponseEntity.ok(movimientoCajaService.listarDeLaJornada(
                jornada != null ? jornada : cajaService.jornadaActual()));
    }

    @Operation(summary = "Anular un movimiento",
            description = "Requiere JWT de dueño. Es baja lógica: la fila queda para poder auditarla.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> anular(@PathVariable Long id,
            @RequestParam(required = false) String motivo) {
        movimientoCajaService.anular(id, motivo);
        return ResponseEntity.noContent().build();
    }
}
