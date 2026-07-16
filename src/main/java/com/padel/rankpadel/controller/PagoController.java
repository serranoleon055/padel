package com.padel.rankpadel.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.LoteReservaRequest;
import com.padel.rankpadel.dto.request.PagoInscripcionRequest;
import com.padel.rankpadel.dto.response.PagoCreadoResponse;
import com.padel.rankpadel.dto.response.PagoResponse;
import com.padel.rankpadel.service.PagoService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @SecurityRequirements({})
    @PostMapping("/reserva")
    public ResponseEntity<PagoCreadoResponse> crearPagoReserva(@Valid @RequestBody LoteReservaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.crearPagoReserva(request));
    }

    @SecurityRequirements({})
    @PostMapping("/inscripcion")
    public ResponseEntity<PagoCreadoResponse> crearPagoInscripcion(@Valid @RequestBody PagoInscripcionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.crearPagoInscripcion(request));
    }

    @SecurityRequirements({})
    @GetMapping("/{referencia}")
    public ResponseEntity<PagoResponse> obtenerPago(@PathVariable String referencia) {
        return ResponseEntity.ok(pagoService.obtenerPorReferencia(referencia));
    }

    @SecurityRequirements({})
    @PostMapping("/{referencia}/cancelar")
    public ResponseEntity<PagoResponse> cancelarPagoReserva(@PathVariable String referencia) {
        return ResponseEntity.ok(pagoService.cancelarPorReferencia(referencia));
    }

    @SecurityRequirements({})
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String topic,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(required = false) String id,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        String tipo = type != null ? type : topic;
        if (esNotificacionDePago(tipo)) {
            pagoService.procesarNotificacion(extraerPagoMercadoPagoId(dataId, id, cuerpo));
        }
        return ResponseEntity.ok().build();
    }

    private boolean esNotificacionDePago(String tipo) {
        return tipo != null && tipo.contains("payment");
    }

    private Long extraerPagoMercadoPagoId(String dataId, String id, Map<String, Object> cuerpo) {
        Long desdeParametros = aLong(dataId != null ? dataId : id);
        if (desdeParametros != null) {
            return desdeParametros;
        }
        if (cuerpo != null && cuerpo.get("data") instanceof Map<?, ?> data) {
            Object valor = data.get("id");
            return valor != null ? aLong(valor.toString()) : null;
        }
        return null;
    }

    private Long aLong(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
