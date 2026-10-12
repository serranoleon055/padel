package com.padel.rankpadel.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Que a un jugador se le mandó un aviso por WhatsApp. El sistema no manda el mensaje (lo
 * abre escrito para que lo mande el organizador), así que lo que se registra es que se
 * abrió para mandar.
 *
 * <p>{@link #clave} es una huella del texto: si después cambia el horario, el mensaje que
 * correspondería ya es otro, la huella no coincide y el panel avisa que hay que reenviarlo.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "avisos_enviados")
public class AvisoEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long torneoId;
    private Long jugadorId;

    /** De qué es el aviso: "DIA:2026-11-14". */
    private String tema;

    private String clave;
    private LocalDateTime enviadoEn;
    private String enviadoPor;
}
