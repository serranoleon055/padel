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
import com.padel.rankpadel.dto.request.LugarRequest;
import com.padel.rankpadel.dto.response.LugarResponse;
import com.padel.rankpadel.service.LugarService;

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
@RequestMapping("/api/lugares")
@Tag(name = "Lugares", description = "Gestión de lugares donde se realizan los torneos.")
public class LugarController {

    private final LugarService lugarService;

    @SecurityRequirements({})
    @Operation(summary = "Listar todos los lugares")
    @ApiResponse(responseCode = "200", description = "Lista de lugares")
    @GetMapping
    public ResponseEntity<List<LugarResponse>> listarTodos() {
        return ResponseEntity.ok(lugarService.listarTodos());
    }

    @SecurityRequirements({})
    @Operation(summary = "Buscar lugar por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lugar encontrado", content = @Content(schema = @Schema(implementation = LugarResponse.class))),
            @ApiResponse(responseCode = "404", description = "Lugar no encontrado", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<LugarResponse> buscarPorId(
            @Parameter(description = "ID del lugar") @PathVariable Long id) {
        return ResponseEntity.ok(lugarService.buscarPorId(id));
    }

    @Operation(summary = "Crear lugar", description = "Requiere JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lugar creado", content = @Content(schema = @Schema(implementation = LugarResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @PostMapping
    public ResponseEntity<LugarResponse> crear(@Valid @RequestBody LugarRequest lugarRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lugarService.crear(lugarRequest));
    }

    @Operation(summary = "Actualizar lugar", description = "Requiere JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lugar actualizado"),
            @ApiResponse(responseCode = "404", description = "Lugar no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<LugarResponse> actualizar(
            @Parameter(description = "ID del lugar") @PathVariable Long id,
            @Valid @RequestBody LugarRequest lugarRequest) {
        return ResponseEntity.ok(lugarService.actualizar(id, lugarRequest));
    }

    @Operation(summary = "Eliminar lugar", description = "Requiere JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lugar eliminado"),
            @ApiResponse(responseCode = "404", description = "Lugar no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @Parameter(description = "ID del lugar") @PathVariable Long id) {
        lugarService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar lugares en lote", description = "Requiere JWT.")
    @PostMapping("/delete-batch")
    public ResponseEntity<?> eliminarBatch(@RequestBody BatchDeleteRequest request) {
        lugarService.eliminarBatch(request.getIds());
        return ResponseEntity.noContent().build();
    }

}
