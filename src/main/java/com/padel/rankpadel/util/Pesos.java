package com.padel.rankpadel.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Montos en los mensajes que lee la persona: "$15.000", "$7.500,50". Nunca "15000.00". */
public final class Pesos {

    private static final DecimalFormatSymbols SIMBOLOS = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR"));

    private Pesos() {
    }

    public static String formatear(BigDecimal monto) {
        if (monto == null) {
            return "$0";
        }
        BigDecimal redondeado = monto.setScale(2, RoundingMode.HALF_UP);
        boolean conCentavos = redondeado.remainder(BigDecimal.ONE).signum() != 0;
        DecimalFormat formato = new DecimalFormat(conCentavos ? "#,##0.00" : "#,##0", SIMBOLOS);
        return "$" + formato.format(redondeado);
    }
}
