package com.padel.rankpadel.controller;

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

import com.padel.rankpadel.dto.request.ConteoRequest;
import com.padel.rankpadel.dto.request.RecuentoRequest;
import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.dto.response.RecuentoResponse;
import com.padel.rankpadel.service.RecuentoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Conteo físico del depósito. Es del dueño: aplicar un conteo da de baja mercadería sin
 * que nadie la haya vendido, que es la forma más limpia de tapar un faltante.
 */
@RestController
@RequestMapping("/api/recuentos")
@RequiredArgsConstructor
@Tag(name = "Recuentos", description = "Conteo físico de inventario")
public class RecuentoController {

    private final RecuentoService recuentoService;

    @Operation(summary = "Abrir un conteo",
            description = "Requiere JWT de dueño. Congela el stock de cada producto para comparar contra lo contado. Uno solo a la vez.")
    @PostMapping
    public ResponseEntity<RecuentoResponse> abrir(@Valid @RequestBody RecuentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recuentoService.abrir(request));
    }

    @Operation(summary = "El conteo abierto",
            description = "Requiere JWT de dueño. Devuelve 204 si no hay ninguno en curso.")
    @GetMapping("/en-curso")
    public ResponseEntity<RecuentoResponse> enCurso() {
        RecuentoResponse enCurso = recuentoService.enCurso();
        return enCurso != null ? ResponseEntity.ok(enCurso) : ResponseEntity.noContent().build();
    }

    @Operation(summary = "Listar conteos", description = "Requiere JWT de dueño.")
    @GetMapping
    public ResponseEntity<PagedResponse<RecuentoResponse>> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {
        return ResponseEntity.ok(recuentoService.listar(pagina, tamanio));
    }

    @Operation(summary = "Ver un conteo", description = "Requiere JWT de dueño.")
    @GetMapping("/{id}")
    public ResponseEntity<RecuentoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(recuentoService.obtener(id));
    }

    @Operation(summary = "Anotar lo contado",
            description = "Requiere JWT de dueño. No toca el stock: eso pasa al aplicar.")
    @PutMapping("/{id}/conteo")
    public ResponseEntity<RecuentoResponse> contar(@PathVariable Long id,
            @Valid @RequestBody ConteoRequest request) {
        return ResponseEntity.ok(recuentoService.contar(id, request));
    }

    @Operation(summary = "Aplicar el conteo",
            description = "Requiere JWT de dueño. Genera un ajuste por cada renglón con diferencia y congela el faltante. No se puede deshacer.")
    @PostMapping("/{id}/aplicar")
    public ResponseEntity<RecuentoResponse> aplicar(@PathVariable Long id) {
        return ResponseEntity.ok(recuentoService.aplicar(id));
    }

    @Operation(summary = "Descartar el conteo",
            description = "Requiere JWT de dueño. Solo mientras esté en borrador: no aplicó nada, así que no hay nada que auditar.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> descartar(@PathVariable Long id) {
        recuentoService.descartar(id);
        return ResponseEntity.noContent().build();
    }
}
