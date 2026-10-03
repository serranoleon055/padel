package com.padel.rankpadel.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfiguracionSedeDto {

    private String email;
    private String telefono;
    private String whatsapp;
    private String instagram;
    private String facebook;
    private String direccion;
    private String mapsEmbedUrl;

    /**
     * Hasta cuántas horas antes del turno el jugador lo puede cancelar solo. En 0 la
     * cancelación por internet queda apagada y solo cancela el club.
     */
    private Integer cancelacionHorasMinimas;

    /** Propuesta de fondo para el formulario de apertura de caja. */
    private java.math.BigDecimal fondoFijo;

    /**
     * Hasta qué porcentaje de la venta puede bonificar el empleado sin el dueño. En 0 no
     * puede descontar nada, que es como arranca: el club que no lo configuró no decidió
     * dar esa atribución.
     */
    private Integer descuentoMaximoMostrador;

    private List<HorarioSede> horarios;
    private List<FotoSede> galeria;
    private List<String> formasPago;

    private String mercadoPagoAccessToken;
    private boolean mercadoPagoConfigurado;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class HorarioSede {
        private String dias;
        private String horas;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FotoSede {
        private String url;
        private String alt;
    }
}
