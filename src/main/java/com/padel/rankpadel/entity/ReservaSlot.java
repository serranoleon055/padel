package com.padel.rankpadel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Un bloque de 30 minutos ocupado por una reserva. El índice único sobre
 * {@code claveSlot} es lo que impide, a nivel base de datos, vender dos veces la misma
 * cancha a la misma hora: con turnos de duración variable una sola clave por reserva ya
 * no alcanza (uno de 90 minutos ocupa tres bloques).
 *
 * <p>Al liberar un turno se borran sus bloques. Si agregás un estado nuevo, decidí
 * primero si libera o no: esa es la regla que tiene que espejar
 * {@code DisponibilidadCanchaService.ESTADOS_ACTIVOS}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "reserva_slots")
public class ReservaSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @Column(name = "clave_slot", nullable = false)
    private String claveSlot;
}
