-- Congela el precio del turno en la propia reserva.
-- Antes, la facturación histórica se calculaba con el precio ACTUAL de la cancha:
-- cada actualización de tarifa reescribía los ingresos de todos los meses pasados.
ALTER TABLE reservas
    ADD COLUMN precio_aplicado DECIMAL(12,2) NULL AFTER hora_fin;

-- Backfill de las reservas existentes con el precio vigente hoy, prorrateado por la
-- duración real del turno (hora_fin - hora_inicio, contemplando el cruce de medianoche).
UPDATE reservas r
    JOIN canchas c ON c.id = r.cancha_id
SET r.precio_aplicado = ROUND(
        c.precio_por_hora
            * (MOD(TIME_TO_SEC(r.hora_fin) - TIME_TO_SEC(r.hora_inicio) + 86400, 86400) / 3600),
        2)
WHERE r.precio_aplicado IS NULL
  AND c.precio_por_hora IS NOT NULL;
