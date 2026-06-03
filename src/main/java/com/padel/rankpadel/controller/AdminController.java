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

import com.padel.rankpadel.dto.request.AdminRequest;
import com.padel.rankpadel.dto.response.AdminResponse;
import com.padel.rankpadel.service.AdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admins")
@Tag(name = "Administradores", description = "Gestion de usuarios administradores. Requiere JWT.")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "Listar administradores")
    @GetMapping
    public ResponseEntity<List<AdminResponse>> listarTodos() {
        return ResponseEntity.ok(adminService.listarTodos());
    }

    @Operation(summary = "Crear administrador")
    @PostMapping
    public ResponseEntity<AdminResponse> crear(@Valid @RequestBody AdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.crear(request));
    }

    @Operation(summary = "Actualizar administrador")
    @PutMapping("/{id}")
    public ResponseEntity<AdminResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody AdminRequest request) {
        return ResponseEntity.ok(adminService.actualizar(id, request));
    }

    @Operation(summary = "Eliminar administrador")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        adminService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
