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

import com.padel.rankpadel.dto.request.ProveedorRequest;
import com.padel.rankpadel.dto.request.VentaRequest;
import com.padel.rankpadel.dto.response.ProveedorResponse;
import com.padel.rankpadel.dto.response.VentaResponse;
import com.padel.rankpadel.service.ProveedorService;
import com.padel.rankpadel.service.VentaService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Ventas", description = "Ventas del mostrador y proveedores")
public class VentaController {

    private final VentaService ventaService;
    private final ProveedorService proveedorService;

    @PostMapping("/ventas")
    public ResponseEntity<VentaResponse> registrar(@Valid @RequestBody VentaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaService.registrar(request));
    }

    @GetMapping("/ventas")
    public ResponseEntity<List<VentaResponse>> listarDelDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(ventaService.listarDelDia(fecha != null ? fecha : LocalDate.now()));
    }

    @GetMapping("/reservas/{reservaId}/ventas")
    public ResponseEntity<List<VentaResponse>> listarDeReserva(@PathVariable Long reservaId) {
        return ResponseEntity.ok(ventaService.listarDeReserva(reservaId));
    }

    /**
     * Baja lógica: la venta deja de sumar y la mercadería vuelve al stock, pero la fila
     * queda. Con {@code confirmado} se acepta anular un consumo que ya se cobró.
     */
    @DeleteMapping("/ventas/{id}")
    public ResponseEntity<VentaResponse> anular(@PathVariable Long id,
            @RequestParam(required = false) String motivo,
            @RequestParam(defaultValue = "false") boolean confirmado) {
        return ResponseEntity.ok(ventaService.anular(id, motivo, confirmado));
    }

    @GetMapping("/proveedores")
    public ResponseEntity<List<ProveedorResponse>> listarProveedores(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return ResponseEntity.ok(proveedorService.listar(soloActivos));
    }

    @PostMapping("/proveedores")
    public ResponseEntity<ProveedorResponse> crearProveedor(@Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proveedorService.crear(request));
    }

    @org.springframework.web.bind.annotation.PutMapping("/proveedores/{id}")
    public ResponseEntity<ProveedorResponse> actualizarProveedor(@PathVariable Long id,
            @Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.ok(proveedorService.actualizar(id, request));
    }

    @DeleteMapping("/proveedores/{id}")
    public ResponseEntity<Void> darDeBajaProveedor(@PathVariable Long id) {
        proveedorService.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }
}
