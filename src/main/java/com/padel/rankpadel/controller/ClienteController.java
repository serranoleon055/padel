package com.padel.rankpadel.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.ClienteRequest;
import com.padel.rankpadel.dto.response.ClienteFichaResponse;
import com.padel.rankpadel.dto.response.ClienteResponse;
import com.padel.rankpadel.service.ClienteService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Ficha unificada de quien alquila canchas: historial, gasto y contacto")
public class ClienteController {

    private static final int TAMANO_MAXIMO = 100;

    private final ClienteService clienteService;

    /** Paginado en el servidor: la lista de clientes crece sin techo. */
    @GetMapping
    public ResponseEntity<Page<ClienteResponse>> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(clienteService.listar(busqueda, Math.max(pagina, 0),
                Math.clamp(tamano, 1, TAMANO_MAXIMO)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteFichaResponse> ficha(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.ficha(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.actualizar(id, request));
    }
}
