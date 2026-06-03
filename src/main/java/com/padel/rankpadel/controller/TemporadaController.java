package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.BatchDeleteRequest;
import com.padel.rankpadel.dto.request.TemporadaRequest;
import com.padel.rankpadel.dto.response.TemporadaResponse;
import com.padel.rankpadel.service.TemporadaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/temporadas")
@Tag(name = "Temporadas", description = "Gestión de temporadas del circuito de pádel.")
public class TemporadaController {

    private final TemporadaService temporadaService;

    @SecurityRequirements({})
    @Operation(summary = "Listar todas las temporadas")
    @ApiResponse(responseCode = "200", description = "Lista de temporadas")
    @GetMapping
    public ResponseEntity<List<TemporadaResponse>> listarTodos() {
        return ResponseEntity.ok(temporadaService.listarTodos());
    }

    @SecurityRequirements({})
    @Operation(summary = "Buscar temporada por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Temporada encontrada", content = @Content(schema = @Schema(implementation = TemporadaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Temporada no encontrada", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<TemporadaResponse> buscarPorId(
            @Parameter(description = "ID de la temporada") @PathVariable Long id) {
        return ResponseEntity.ok(temporadaService.buscarPorId(id));
    }

    @Operation(summary = "Crear temporada", description = "Requiere JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Temporada creada", content = @Content(schema = @Schema(implementation = TemporadaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TemporadaResponse> crear(@Valid @RequestBody TemporadaRequest temporadaRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(temporadaService.crear(temporadaRequest));
    }

    @Operation(summary = "Actualizar temporada", description = "Requiere JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Temporada actualizada"),
            @ApiResponse(responseCode = "404", description = "Temporada no encontrada", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<TemporadaResponse> actualizar(
            @Parameter(description = "ID de la temporada") @PathVariable Long id,
            @Valid @RequestBody TemporadaRequest temporadaRequest) {
        return ResponseEntity.ok(temporadaService.actualizar(id, temporadaRequest));
    }

    @Operation(summary = "Eliminar temporada", description = "Requiere JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Temporada eliminada"),
            @ApiResponse(responseCode = "404", description = "Temporada no encontrada", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @Parameter(description = "ID de la temporada") @PathVariable Long id) {
        temporadaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar temporadas en lote", description = "Requiere JWT.")
    @PostMapping("/delete-batch")
    public ResponseEntity<?> eliminarBatch(@RequestBody BatchDeleteRequest request) {
        temporadaService.eliminarBatch(request.getIds());
        return ResponseEntity.noContent().build();
    }

}
