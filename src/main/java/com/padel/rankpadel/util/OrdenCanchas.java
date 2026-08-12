package com.padel.rankpadel.util;

import java.util.Comparator;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orden de las canchas: por número, no por texto.
 *
 * <p>El club las nombra "Cancha 1", "Cancha 2"... y las mira en ese orden. Ordenar por el
 * nombre como string pone la 10 antes que la 2, y sin ningún orden salen como se
 * crearon. Se compara el primer número que aparezca en el nombre; las que no tienen
 * número van al final, ordenadas alfabéticamente.
 */
public final class OrdenCanchas {

    private static final Pattern NUMERO = Pattern.compile("\\d+");

    private OrdenCanchas() {
    }

    private static int numeroDe(String nombre) {
        if (nombre == null) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = NUMERO.matcher(nombre);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Comparador para cualquier objeto del que se pueda sacar el nombre de la cancha. */
    public static <T> Comparator<T> porNombre(Function<T, String> nombre) {
        return Comparator
                .comparingInt((T item) -> numeroDe(nombre.apply(item)))
                .thenComparing(item -> nombre.apply(item) == null ? "" : nombre.apply(item),
                        String.CASE_INSENSITIVE_ORDER);
    }

}
