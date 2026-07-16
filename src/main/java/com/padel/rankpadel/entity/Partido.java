package com.padel.rankpadel.entity;

import java.time.LocalDateTime;

import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;

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
@Table(name = "partidos")
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String marcador;

    @Enumerated(EnumType.STRING)
    private EstadoPartido estado;

    @Enumerated(EnumType.STRING)
    private FasePartido fase;

    private LocalDateTime fechaHora;
    private LocalDateTime fechaHoraProgramada;

    private Integer ordenLlave;

    private Integer jornada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id")
    private Cancha cancha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id")
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ronda_id")
    private RondaEliminatorias ronda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pareja_local_id")
    private Pareja local;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pareja_visitante_id")
    private Pareja visitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ganador_id")
    private Pareja ganador;

    // Auditoría: qué admin cargó/modificó el resultado y cuándo (para disputas)
    private String resultadoCargadoPor;
    private LocalDateTime resultadoCargadoEn;

}
