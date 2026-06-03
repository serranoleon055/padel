package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.response.PartidoResponse;
import com.padel.rankpadel.service.PartidoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/partidos")
@Tag(name = "Partidos", description = "Consultas publicas de partidos para resultados y vivo.")
public class PartidoController {

    private final PartidoService partidoService;

    @SecurityRequirements({})
    @Operation(summary = "Listar ultimos resultados")
    @ApiResponse(responseCode = "200", description = "Ultimos resultados devueltos exitosamente")
    @GetMapping("/ultimos-resultados")
    public ResponseEntity<List<PartidoResponse>> listarUltimosResultados() {
        return ResponseEntity.ok(partidoService.listarUltimosResultados());
    }

    @SecurityRequirements({})
    @Operation(summary = "Listar partidos en vivo")
    @ApiResponse(responseCode = "200", description = "Partidos en vivo devueltos exitosamente")
    @GetMapping("/en-vivo")
    public ResponseEntity<List<PartidoResponse>> listarEnVivo() {
        return ResponseEntity.ok(partidoService.listarEnVivo());
    }

    @SecurityRequirements({})
    @Operation(summary = "Próximos partidos programados", description = "Devuelve los partidos con fecha/hora programada de todos los torneos activos, desde hoy en adelante.")
    @GetMapping("/proximos")
    public ResponseEntity<List<PartidoResponse>> listarProximos() {
        return ResponseEntity.ok(partidoService.listarProximos());
    }

}
