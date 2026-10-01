package com.padel.rankpadel.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Quién está pidiendo, para los endpoints que son públicos pero muestran algo de más a
 * quien entró al panel.
 *
 * <p>Los listados de jugadores, sedes y canchas los consume también el sitio público, así
 * que no se pueden cerrar. Pero las filas dadas de baja son cosas que el club decidió
 * esconder, y el parámetro que las muestra lo puede mandar cualquiera a mano: por eso el
 * flag se honra solo si además hay un admin autenticado.
 *
 * <p>No reemplaza a las reglas de {@code SecurityConfig}, que son las que deciden quién
 * puede entrar a cada endpoint. Esto decide cuánto se ve dentro de uno que ya es público.
 */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    /** Si quien pide entró al panel, sea dueño o mostrador. */
    public static boolean esAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream()
                        .anyMatch(rol -> "ROLE_ADMIN".equals(rol.getAuthority()));
    }
}
