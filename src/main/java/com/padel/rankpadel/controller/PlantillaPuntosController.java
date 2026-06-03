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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.PlantillaPuntosRequest;
import com.padel.rankpadel.dto.response.PlantillaPuntosResponse;
import com.padel.rankpadel.service.PlantillaPuntosService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/plantillas-puntos")
public class PlantillaPuntosController {

    private final PlantillaPuntosService plantillaPuntosService;

    @GetMapping
    public ResponseEntity<List<PlantillaPuntosResponse>> listarTodos(
            @RequestParam(name = "soloActivas", required = false) Boolean soloActivas) {
        return ResponseEntity.ok(plantillaPuntosService.listarTodos(soloActivas));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlantillaPuntosResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(plantillaPuntosService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<PlantillaPuntosResponse> crear(@Valid @RequestBody PlantillaPuntosRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plantillaPuntosService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlantillaPuntosResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PlantillaPuntosRequest request) {
        return ResponseEntity.ok(plantillaPuntosService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        plantillaPuntosService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
