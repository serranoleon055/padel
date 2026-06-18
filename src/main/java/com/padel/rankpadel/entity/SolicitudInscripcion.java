package com.padel.rankpadel.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.EstadoSolicitud;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.enums.PosicionJuego;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "solicitudes_inscripcion")
public class SolicitudInscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    private EstadoSolicitud estado;

    private String telefonoContacto;
    private LocalDateTime creadoEn;

    @Builder.Default
    private boolean pagada = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id")
    private Pago pago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador1_id")
    private Jugador jugador1;

    @Column(name = "jugador1_nombre")
    private String jugador1Nombre;
    @Column(name = "jugador1_apellido")
    private String jugador1Apellido;
    @Enumerated(EnumType.STRING)
    @Column(name = "jugador1_genero")
    private Genero jugador1Genero;
    @Column(name = "jugador1_telefono")
    private String jugador1Telefono;
    @Column(name = "jugador1_fecha_nacimiento")
    private LocalDate jugador1FechaNacimiento;
    @Enumerated(EnumType.STRING)
    @Column(name = "jugador1_posicion_juego")
    private PosicionJuego jugador1PosicionJuego;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador2_id")
    private Jugador jugador2;

    @Column(name = "jugador2_nombre")
    private String jugador2Nombre;
    @Column(name = "jugador2_apellido")
    private String jugador2Apellido;
    @Enumerated(EnumType.STRING)
    @Column(name = "jugador2_genero")
    private Genero jugador2Genero;
    @Column(name = "jugador2_telefono")
    private String jugador2Telefono;
    @Column(name = "jugador2_fecha_nacimiento")
    private LocalDate jugador2FechaNacimiento;
    @Enumerated(EnumType.STRING)
    @Column(name = "jugador2_posicion_juego")
    private PosicionJuego jugador2PosicionJuego;
}
