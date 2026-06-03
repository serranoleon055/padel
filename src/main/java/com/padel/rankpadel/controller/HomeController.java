package com.padel.rankpadel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.response.AdminDashboardResponse;
import com.padel.rankpadel.dto.response.HomeResponse;
import com.padel.rankpadel.dto.response.HomeSummaryResponse;
import com.padel.rankpadel.service.HomeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/home")
@Tag(name = "Home", description = "Datos agregados para construir la home publica de RankPadel.")
public class HomeController {

    private final HomeService homeService;

    @SecurityRequirements({})
    @Operation(summary = "Obtener datos completos de la home publica")
    @ApiResponse(responseCode = "200", description = "Datos de home devueltos exitosamente")
    @GetMapping
    public ResponseEntity<HomeResponse> obtenerHome() {
        return ResponseEntity.ok(homeService.obtenerHome());
    }

    @SecurityRequirements({})
    @Operation(summary = "Obtener metricas principales para hero/dashboard")
    @ApiResponse(responseCode = "200", description = "Metricas devueltas exitosamente")
    @GetMapping("/summary")
    public ResponseEntity<HomeSummaryResponse> obtenerSummary() {
        return ResponseEntity.ok(homeService.obtenerSummary());
    }

    @Operation(summary = "Obtener datos completos del dashboard admin", description = "Requiere JWT. Incluye metricas, temporada activa, ultimos torneos, evolucion.")
    @GetMapping("/admin-dashboard")
    public ResponseEntity<AdminDashboardResponse> obtenerDashboard() {
        return ResponseEntity.ok(homeService.obtenerDashboard());
    }

}
