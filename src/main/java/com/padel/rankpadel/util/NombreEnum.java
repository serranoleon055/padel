package com.padel.rankpadel.util;

/**
 * El nombre de un enum escrito como lo lee una persona.
 *
 * <p>Los enums viajan crudos a la API y el front tiene sus propios rótulos, que es lo
 * correcto: la pantalla decide cómo se dice cada cosa. Pero hay textos que arma el
 * backend —la descripción de un movimiento de cuenta corriente, el motivo de un
 * movimiento de stock, un mensaje de error— y ahí el nombre crudo sale gritado y con
 * guión bajo: "Anulación de REMITO 0001-12" o "Pago MERCADO_PAGO".
 */
public final class NombreEnum {

    private NombreEnum() {
    }

    /** {@code MERCADO_PAGO} → {@code "mercado pago"}. */
    public static String enMinuscula(Enum<?> valor) {
        return valor == null ? "" : valor.name().replace('_', ' ').toLowerCase();
    }

    /** {@code REMITO} → {@code "Remito"}. Para lo que arranca una frase. */
    public static String capitalizado(Enum<?> valor) {
        String texto = enMinuscula(valor);
        return texto.isEmpty() ? texto : texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
