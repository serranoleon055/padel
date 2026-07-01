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
