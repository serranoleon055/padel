package com.padel.rankpadel.dto.response;

import java.time.LocalTime;
import java.util.List;

/**
 * Qué franjas quedan libres para vender como abono, por día de la semana y por cancha.
 *
 * <p>Es la grilla que el club publica: "turnos fijos disponibles". Lo que ocupa acá es el
 * abono de otro cliente, no el turno suelto de la semana que viene: un abono se toma todas
 * las semanas, así que la pregunta es cuáles de esas horas no están comprometidas de forma
 * permanente.
 */
public record DisponibilidadAbonosResponse(
        List<Dia> dias,
        List<Cancha> canchas) {

    /** Una cancha del lugar, para que la grilla arme sus columnas en un orden estable. */
    public record Cancha(Long id, String nombre) {}

    /** {@code diaSemana} es ISO: 1 = lunes ... 7 = domingo. */
    public record Dia(int diaSemana, String nombre, List<CanchaDelDia> canchas) {}

    /**
     * {@code cerrada} distingue "el club no atiende / la cancha no tiene horario cargado"
     * de "atiende pero está todo vendido". En la grilla no es lo mismo un guion que un
     * "sin lugar", y sin el dato las dos cosas se veían igual.
     */
    public record CanchaDelDia(Long canchaId, String canchaNombre, boolean cerrada, List<Franja> franjas) {}

    public record Franja(LocalTime horaDesde, LocalTime horaHasta) {}
}
