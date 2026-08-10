package com.padel.rankpadel.util;

/**
 * Forma canónica de un teléfono argentino, para reconocer al mismo cliente aunque lo
 * escriba distinto cada vez: {@code 0385 15 689-4061}, {@code +54 9 385 6894061} y
 * {@code 3856894061} son la misma persona.
 *
 * <p>El resultado es solo dígitos: código de área + número, sin 54, sin 9 y sin 0.
 *
 * <p>Si cambiás estas reglas, actualizá también {@code shared/lib/whatsapp.ts} del
 * frontend y el backfill de la migración V43, que implementan lo mismo.
 */
public final class NormalizadorTelefono {

    /** Un número argentino completo (área + abonado) mide 10 dígitos. */
    private static final int LARGO_NORMAL = 10;
    private static final int LARGO_MINIMO = 8;

    private NormalizadorTelefono() {
    }

    public static String normalizar(String telefono) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return null;
        }
        if (digitos.startsWith("54")) {
            digitos = digitos.substring(2);
        }
        if (digitos.startsWith("9")) {
            digitos = digitos.substring(1);
        }
        if (digitos.startsWith("0")) {
            digitos = digitos.substring(1);
        }
        // El 15 va después del código de área. Solo se saca si el número quedó más largo
        // de lo normal: un 11 5689 4061 legítimo ya mide 10 y no hay que tocarlo.
        if (digitos.length() > LARGO_NORMAL) {
            digitos = digitos.replaceFirst("^(\\d{2,4})15(\\d{6,8})$", "$1$2");
        }
        return digitos.length() >= LARGO_MINIMO ? digitos : null;
    }
}
