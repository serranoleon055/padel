package com.padel.rankpadel.util;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.padel.rankpadel.entity.Partido;

/** Estampa quién y cuándo cargó/modificó el resultado de un partido. */
public final class AuditoriaResultado {

    private AuditoriaResultado() {
    }

    public static void marcar(Partido partido) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        partido.setResultadoCargadoPor(auth != null ? auth.getName() : null);
        partido.setResultadoCargadoEn(LocalDateTime.now());
    }
}
