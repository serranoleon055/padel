package com.padel.rankpadel.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.padel.rankpadel.dto.request.HorarioCanchaRequest;
import com.padel.rankpadel.dto.response.HorarioCanchaResponse;
import com.padel.rankpadel.service.HorarioCanchaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/horarios-cancha")
@RequiredArgsConstructor
public class HorarioCanchaController {

    private final HorarioCanchaService horarioCanchaService;

    @PostMapping
    public ResponseEntity<HorarioCanchaResponse> guardar(@Valid @RequestBody HorarioCanchaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(horarioCanchaService.guardar(request));
    }

    @GetMapping
    public ResponseEntity<List<HorarioCanchaResponse>> listar(@RequestParam Long canchaId) {
        return ResponseEntity.ok(horarioCanchaService.listarPorCancha(canchaId));
    }
}
