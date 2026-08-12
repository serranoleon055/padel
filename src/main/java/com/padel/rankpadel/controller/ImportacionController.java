package com.padel.rankpadel.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.padel.rankpadel.dto.response.ImportacionResponse;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.service.ImportacionService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Importación desde la planilla que el club ya tiene.
 *
 * <p>Con {@code vistaPrevia=true} no se escribe nada: devuelve el mismo informe que
 * devolvería la importación real, para que el club lo revise antes de confirmar.
 */
@RestController
@RequestMapping("/api/importar")
@RequiredArgsConstructor
@Tag(name = "Importación", description = "Alta masiva de jugadores y clientes desde CSV")
public class ImportacionController {

    private static final long MAXIMO_BYTES = 1_000_000;

    private final ImportacionService importacionService;

    @PostMapping("/jugadores")
    public ResponseEntity<ImportacionResponse> jugadores(@RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean vistaPrevia) {
        return ResponseEntity.ok(importacionService.importarJugadores(texto(file), vistaPrevia));
    }

    @PostMapping("/clientes")
    public ResponseEntity<ImportacionResponse> clientes(@RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean vistaPrevia) {
        return ResponseEntity.ok(importacionService.importarClientes(texto(file), vistaPrevia));
    }

    /**
     * El Excel del club guarda en UTF-8 con BOM o en Latin-1 según cómo se exportó. Se lee
     * como UTF-8 y, si aparece el carácter de reemplazo, se reintenta en Latin-1: es la
     * diferencia entre "Martín" y "MartÃ­n" en toda la planilla.
     */
    private String texto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EstadoInvalidoException("Elegí un archivo CSV");
        }
        if (file.getSize() > MAXIMO_BYTES) {
            throw new EstadoInvalidoException("El archivo es demasiado grande (máximo 1 MB)");
        }
        try {
            byte[] bytes = file.getBytes();
            String comoUtf8 = new String(bytes, StandardCharsets.UTF_8);
            return comoUtf8.indexOf('�') >= 0 ? new String(bytes, StandardCharsets.ISO_8859_1) : comoUtf8;
        } catch (IOException e) {
            throw new EstadoInvalidoException("No se pudo leer el archivo");
        }
    }
}
