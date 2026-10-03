package com.padel.rankpadel.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.AjusteStockRequest;
import com.padel.rankpadel.dto.request.MovimientoStockRequest;
import com.padel.rankpadel.dto.request.PresentacionRequest;
import com.padel.rankpadel.dto.request.ProductoRequest;
import com.padel.rankpadel.dto.response.MovimientoStockResponse;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.dto.response.PresentacionResponse;
import com.padel.rankpadel.dto.response.ProductoResponse;
import com.padel.rankpadel.service.PresentacionProductoService;
import com.padel.rankpadel.service.ProductoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Catálogo del mostrador y control de stock")
public class ProductoController {

    private final ProductoService productoService;
    private final PresentacionProductoService presentacionProductoService;

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

    @Operation(summary = "Kardex del producto",
            description = "Requiere JWT de admin. Entradas y salidas con las unidades que quedaban después de cada una. El rango de fechas acota lo que se lista, no el saldo.")
    @GetMapping("/{id}/movimientos")
    public ResponseEntity<PagedResponse<MovimientoStockResponse>> movimientos(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamanio) {
        // `hasta` se recibe como día y se extiende al final de ese día: pedir "hasta el 15"
        // y que no aparezca nada del 15 porque los movimientos tienen hora es un error que
        // el que mira el kardex no tiene por qué deducir.
        return ResponseEntity.ok(productoService.movimientos(id,
                desde != null ? desde.atStartOfDay() : null,
                hasta != null ? hasta.atTime(LocalTime.MAX) : null,
                pagina, tamanio));
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
            @Valid @RequestBody AjusteStockRequest request) {
        return ResponseEntity.ok(productoService.ajustar(id, request.getStockReal(), request.getNotas()));
    }

    /**
     * Las formas de vender el producto: la unidad suelta y los packs. El mostrador las
     * lee para elegir cómo cobrar; cargarlas es decisión del dueño, igual que el precio.
     */
    @Operation(summary = "Presentaciones de un producto")
    @GetMapping("/{id}/presentaciones")
    public ResponseEntity<List<PresentacionResponse>> listarPresentaciones(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean incluirBajas) {
        return ResponseEntity.ok(presentacionProductoService.listar(id, incluirBajas));
    }

    @Operation(summary = "Crear presentación", description = "Requiere JWT de dueño.")
    @PostMapping("/{id}/presentaciones")
    public ResponseEntity<PresentacionResponse> crearPresentacion(@PathVariable Long id,
            @Valid @RequestBody PresentacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(presentacionProductoService.crear(id, request));
    }

    @Operation(summary = "Editar presentación", description = "Requiere JWT de dueño.")
    @PutMapping("/presentaciones/{presentacionId}")
    public ResponseEntity<PresentacionResponse> actualizarPresentacion(@PathVariable Long presentacionId,
            @Valid @RequestBody PresentacionRequest request) {
        return ResponseEntity.ok(presentacionProductoService.actualizar(presentacionId, request));
    }

    @Operation(summary = "Dar de baja una presentación",
            description = "Requiere JWT de dueño. Baja lógica: las ventas viejas la siguen mostrando.")
    @DeleteMapping("/presentaciones/{presentacionId}")
    public ResponseEntity<Void> darDeBajaPresentacion(@PathVariable Long presentacionId) {
        presentacionProductoService.darDeBaja(presentacionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar una presentación", description = "Requiere JWT de dueño.")
    @PatchMapping("/presentaciones/{presentacionId}/reactivar")
    public ResponseEntity<PresentacionResponse> reactivarPresentacion(@PathVariable Long presentacionId) {
        return ResponseEntity.ok(presentacionProductoService.reactivar(presentacionId));
    }

    @PostMapping("/{id}/mermas")
    public ResponseEntity<ProductoResponse> registrarMerma(@PathVariable Long id,
            @Valid @RequestBody MovimientoStockRequest request) {
        return ResponseEntity.ok(productoService.registrarMerma(id, request));
    }
}
