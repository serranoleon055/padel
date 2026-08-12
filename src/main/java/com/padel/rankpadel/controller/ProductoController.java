package com.padel.rankpadel.controller;

import java.util.List;
import java.util.Map;

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

import com.padel.rankpadel.dto.request.MovimientoStockRequest;
import com.padel.rankpadel.dto.request.ProductoRequest;
import com.padel.rankpadel.dto.response.MovimientoStockResponse;
import com.padel.rankpadel.dto.response.ProductoResponse;
import com.padel.rankpadel.service.ProductoService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Catálogo del mostrador y control de stock")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<ProductoResponse>> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return ResponseEntity.ok(productoService.listar(busqueda, soloActivos));
    }

    /** Lo que hay que reponer, para el aviso del panel. */
    @GetMapping("/stock-bajo")
    public ResponseEntity<List<ProductoResponse>> stockBajo() {
        return ResponseEntity.ok(productoService.conStockBajo());
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(productoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Long id) {
        productoService.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/movimientos")
    public ResponseEntity<List<MovimientoStockResponse>> movimientos(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.movimientos(id));
    }

    /** Historial de compras de todo el club, no de un producto. */
    @GetMapping("/compras")
    public ResponseEntity<List<MovimientoStockResponse>> compras() {
        return ResponseEntity.ok(productoService.compras());
    }

    /** Entrada de mercadería. Con medio de pago, además registra el egreso. */
    @PostMapping("/{id}/compras")
    public ResponseEntity<ProductoResponse> comprar(@PathVariable Long id,
            @Valid @RequestBody MovimientoStockRequest request) {
        return ResponseEntity.ok(productoService.comprar(id, request));
    }

    /** Corrección tras contar la vitrina: se manda cuántas unidades hay de verdad. */
    @PostMapping("/{id}/ajustes")
    public ResponseEntity<ProductoResponse> ajustar(@PathVariable Long id,
            @RequestBody Map<String, Object> cuerpo) {
        int stockReal = Integer.parseInt(String.valueOf(cuerpo.getOrDefault("stockReal", 0)));
        String notas = cuerpo.get("notas") != null ? String.valueOf(cuerpo.get("notas")) : null;
        return ResponseEntity.ok(productoService.ajustar(id, stockReal, notas));
    }

    @PostMapping("/{id}/mermas")
    public ResponseEntity<ProductoResponse> registrarMerma(@PathVariable Long id,
            @Valid @RequestBody MovimientoStockRequest request) {
        return ResponseEntity.ok(productoService.registrarMerma(id, request));
    }
}
