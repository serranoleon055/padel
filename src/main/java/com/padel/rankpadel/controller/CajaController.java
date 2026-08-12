package com.padel.rankpadel.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.padel.rankpadel.dto.request.CierreCajaRequest;
import com.padel.rankpadel.dto.request.CobroRequest;
import com.padel.rankpadel.dto.request.GastoRequest;
import com.padel.rankpadel.dto.response.CierreCajaResponse;
import com.padel.rankpadel.dto.response.CobroResponse;
import com.padel.rankpadel.dto.response.GastoResponse;
import com.padel.rankpadel.service.CajaService;
import com.padel.rankpadel.service.CobroService;
import com.padel.rankpadel.service.GastoService;

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
    private final GastoService gastoService;

    @PostMapping("/reservas/{reservaId}/cobros")
    public ResponseEntity<CobroResponse> registrarCobro(@PathVariable Long reservaId,
            @Valid @RequestBody CobroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cobroService.registrar(reservaId, request));
    }

    @GetMapping("/reservas/{reservaId}/cobros")
    public ResponseEntity<java.util.List<CobroResponse>> listarCobros(@PathVariable Long reservaId) {
        return ResponseEntity.ok(cobroService.listarDeReserva(reservaId));
    }

    /** Baja lógica: el cobro deja de sumar pero la fila queda con el autor y el motivo. */
    @DeleteMapping("/cobros/{id}")
    public ResponseEntity<CobroResponse> anularCobro(@PathVariable Long id,
            @RequestParam(required = false) String motivo) {
        return ResponseEntity.ok(cobroService.anular(id, motivo));
    }

    @GetMapping("/caja")
    public ResponseEntity<CierreCajaResponse> cierre(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(cajaService.cierre(fecha != null ? fecha : LocalDate.now()));
    }

    /** Firma el arqueo del día: alguien contó el cajón y deja asentado cuánto había. */
    @PostMapping("/caja/cierre")
    public ResponseEntity<CierreCajaResponse> cerrar(@Valid @RequestBody CierreCajaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cajaService.cerrar(request));
    }

    /** Reabre un día cerrado para poder corregirlo. Queda registrado en el log. */
    @DeleteMapping("/caja/cierre")
    public ResponseEntity<CierreCajaResponse> reabrir(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(cajaService.reabrir(fecha));
    }

    @PostMapping("/gastos")
    public ResponseEntity<GastoResponse> registrarGasto(@Valid @RequestBody GastoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gastoService.registrar(request));
    }

    @GetMapping("/gastos")
    public ResponseEntity<java.util.List<GastoResponse>> listarGastos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(gastoService.listarEntre(desde, hasta));
    }

    @PutMapping("/gastos/{id}")
    public ResponseEntity<GastoResponse> actualizarGasto(@PathVariable Long id,
            @Valid @RequestBody GastoRequest request) {
        return ResponseEntity.ok(gastoService.actualizar(id, request));
    }

    @DeleteMapping("/gastos/{id}")
    public ResponseEntity<Void> eliminarGasto(@PathVariable Long id) {
        gastoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
