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

import com.padel.rankpadel.dto.request.PlantillaFormatoRequest;
import com.padel.rankpadel.dto.response.PlantillaFormatoResponse;
import com.padel.rankpadel.service.PlantillaFormatoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/plantillas-formato")
public class PlantillaFormatoController {

    private final PlantillaFormatoService plantillaFormatoService;

    @GetMapping
    public ResponseEntity<List<PlantillaFormatoResponse>> listarTodos(
            @RequestParam(name = "soloActivas", required = false) Boolean soloActivas) {
        return ResponseEntity.ok(plantillaFormatoService.listarTodos(soloActivas));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlantillaFormatoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(plantillaFormatoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<PlantillaFormatoResponse> crear(@Valid @RequestBody PlantillaFormatoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plantillaFormatoService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlantillaFormatoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PlantillaFormatoRequest request) {
        return ResponseEntity.ok(plantillaFormatoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        plantillaFormatoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
