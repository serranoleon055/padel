package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.response.PagedResponse;
import com.padel.rankpadel.dto.response.RankingResponse;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.service.RankingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
@Tag(name = "Ranking", description = "Consulta de ranking de jugadores. Endpoint completamente público.")
public class RankingController {

    private final RankingService rankingService;

    @SecurityRequirements({})
    @Operation(summary = "Obtener ranking paginado")
    @GetMapping("/paginado")
    public ResponseEntity<PagedResponse<RankingResponse>> obtenerRankingPaginado(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Genero genero,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {
        return ResponseEntity.ok(PagedResponse.of(rankingService.obtenerRanking(categoriaId, genero), pagina, tamanio));
    }

    @SecurityRequirements({})
    @Operation(summary = "Obtener ranking de jugadores", description = """
            Devuelve el ranking global o filtrado. Parámetros opcionales:
            - `categoriaId`: filtra por categoría específica
            - `genero`: filtra por género (`MASCULINO` o `FEMENINO`)

            El ranking incluye posición actual, anterior, puntos, torneos jugados, victorias, derrotas y tendencia (SUBE/BAJA/IGUAL).
            """)
    @ApiResponse(responseCode = "200", description = "Ranking devuelto exitosamente")
    @GetMapping
    public ResponseEntity<List<RankingResponse>> obtenerRanking(
            @Parameter(description = "ID de la categoría (opcional)") @RequestParam(required = false) Long categoriaId,
            @Parameter(description = "Género para filtrar: MASCULINO o FEMENINO (opcional)") @RequestParam(required = false) Genero genero) {
        return ResponseEntity.ok(rankingService.obtenerRanking(categoriaId, genero));
    }

    @Operation(summary = "Recalcular los puntos del ranking", description = """
            Requiere JWT de admin. Resetea los puntos acumulados y los vuelve a sumar a partir de los
            partidos finalizados de torneos que suman al ranking, usando los nombres de ronda normalizados.
            Útil para corregir datos cargados antes de un arreglo de cálculo de puntos.
            """)
    @ApiResponse(responseCode = "200", description = "Ranking recalculado")
    @PostMapping("/recalcular")
    public ResponseEntity<String> recalcularPuntos() {
        int partidos = rankingService.recalcularPuntos();
        return ResponseEntity.ok("Ranking recalculado a partir de " + partidos + " partidos.");
    }
}