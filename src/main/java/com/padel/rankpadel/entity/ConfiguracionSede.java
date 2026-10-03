package com.padel.rankpadel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "configuracion_sede")
public class ConfiguracionSede {

    @Id
    private Long id;

    private String email;
    private String telefono;

    /**
     * Hasta cuántas horas antes del turno el jugador lo puede cancelar solo desde el
     * enlace que le llegó. En 0 se apaga: solo cancela el club.
     */
    @Builder.Default
    private Integer cancelacionHorasMinimas = 12;

    /**
     * Hasta qué porcentaje de la venta puede bonificar el mostrador sin el dueño.
     *
     * <p>En 0 (el default) no puede descontar nada: el club que no lo configuró no decidió
     * dar esa atribución, y arrancar permitiendo sería decidir por él. El dueño nunca
     * tiene tope.
     */
    @Builder.Default
    private Integer descuentoMaximoMostrador = 0;

    /**
     * El cambio que el club deja en el cajón para dar vuelto. Es solo la propuesta del
     * formulario de apertura: lo que entra al arqueo es el monto que se declara al abrir,
     * no este número.
     */
    private java.math.BigDecimal fondoFijo;

    private String whatsapp;
    private String instagram;
    private String facebook;
    private String direccion;

    @Column(columnDefinition = "TEXT")
    private String mapsEmbedUrl;

    @Column(columnDefinition = "TEXT")
    private String horariosJson;

    @Column(columnDefinition = "TEXT")
    private String galeriaJson;

    @Column(columnDefinition = "TEXT")
    private String formasPagoJson;

    @Column(columnDefinition = "TEXT")
    private String mercadoPagoAccessToken;
}
