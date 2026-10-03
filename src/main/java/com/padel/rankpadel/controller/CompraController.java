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

import com.padel.rankpadel.dto.request.CompraRequest;
import com.padel.rankpadel.dto.request.PagoProveedorRequest;
import com.padel.rankpadel.dto.response.CompraResponse;
import com.padel.rankpadel.dto.response.CuentaProveedorResponse;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.service.CompraService;
import com.padel.rankpadel.service.CuentaProveedorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Compras a proveedor y cuenta corriente. Todo acá es del dueño: es la plata que sale
 * del club.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Compras", description = "Compras a proveedor y cuenta corriente")
public class CompraController {

    private final CompraService compraService;
    private final CuentaProveedorService cuentaProveedorService;

    @Operation(summary = "Cargar una compra",
            description = "Requiere JWT de dueño. Un comprobante con varios productos: genera un egreso y la entrada de stock de cada renglón. Sin medio de pago queda a cuenta corriente.")
    @PostMapping("/compras")
    public ResponseEntity<CompraResponse> registrar(@Valid @RequestBody CompraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(compraService.registrar(request));
    }

    @Operation(summary = "Listar compras", description = "Requiere JWT de dueño.")
    @GetMapping("/compras")
    public ResponseEntity<PagedResponse<CompraResponse>> listar(
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {
        return ResponseEntity.ok(compraService.listar(proveedorId, pagina, tamanio));
    }

    @Operation(summary = "Ver una compra", description = "Requiere JWT de dueño.")
    @GetMapping("/compras/{id}")
    public ResponseEntity<CompraResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(compraService.obtener(id));
    }

    @Operation(summary = "Anular una compra",
            description = "Requiere JWT de dueño. Revierte el stock de todos los renglones y anula el egreso. Los movimientos originales quedan: se emiten compensatorios.")
    @DeleteMapping("/compras/{id}")
    public ResponseEntity<Void> anular(@PathVariable Long id,
            @RequestParam(required = false) String motivo) {
        compraService.anular(id, motivo);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cuenta corriente de un proveedor",
            description = "Requiere JWT de dueño. Compras a cuenta menos pagos, con el saldo fila por fila.")
    @GetMapping("/proveedores/{id}/cuenta")
    public ResponseEntity<CuentaProveedorResponse> cuenta(@PathVariable Long id) {
        return ResponseEntity.ok(cuentaProveedorService.cuenta(id));
    }

    @Operation(summary = "Pagarle al proveedor",
            description = "Requiere JWT de dueño. Esto sí sale del cajón: la compra a cuenta no.")
    @PostMapping("/proveedores/{id}/pagos")
    public ResponseEntity<CuentaProveedorResponse> pagar(@PathVariable Long id,
            @Valid @RequestBody PagoProveedorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentaProveedorService.pagar(id, request));
    }

    @Operation(summary = "Anular un pago a proveedor", description = "Requiere JWT de dueño.")
    @DeleteMapping("/proveedores/pagos/{pagoId}")
    public ResponseEntity<Void> anularPago(@PathVariable Long pagoId,
            @RequestParam(required = false) String motivo) {
        cuentaProveedorService.anularPago(pagoId, motivo);
        return ResponseEntity.noContent().build();
    }
}
