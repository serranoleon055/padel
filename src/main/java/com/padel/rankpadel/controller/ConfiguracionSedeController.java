package com.padel.rankpadel.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.padel.rankpadel.dto.ConfiguracionSedeDto;
import com.padel.rankpadel.service.ConfiguracionSedeService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/configuracion-sede")
@RequiredArgsConstructor
public class ConfiguracionSedeController {

    private final ConfiguracionSedeService configuracionSedeService;

    @SecurityRequirements({})
    @GetMapping
    public ResponseEntity<ConfiguracionSedeDto> obtener() {
        return ResponseEntity.ok(configuracionSedeService.obtener());
    }

    @PutMapping
    public ResponseEntity<ConfiguracionSedeDto> actualizar(@RequestBody ConfiguracionSedeDto request) {
        return ResponseEntity.ok(configuracionSedeService.actualizar(request));
    }

    @PostMapping("/galeria/imagen")
    public ResponseEntity<Map<String, String>> subirImagenGaleria(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(Map.of("url", configuracionSedeService.subirImagenGaleria(file)));
    }
}
