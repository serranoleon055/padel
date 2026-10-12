package com.padel.rankpadel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.response.PanelOrganizadorResponse;
import com.padel.rankpadel.service.PanelOrganizadorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/organizador")
@RequiredArgsConstructor
@Tag(name = "Panel del organizador", description = "Torneos en marcha, alertas y resultados")
public class PanelOrganizadorController {

    private final PanelOrganizadorService panelOrganizadorService;

    @Operation(summary = "Panel del organizador", description = "Requiere JWT. Los montos solo para el dueño.")
    @GetMapping("/panel")
    public ResponseEntity<PanelOrganizadorResponse> panel() {
        return ResponseEntity.ok(panelOrganizadorService.panel());
    }
}
