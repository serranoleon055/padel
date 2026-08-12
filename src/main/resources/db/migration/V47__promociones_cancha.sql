-- De "precio por franja horaria" a "promociones".
--
-- La franja permanente no existe en la realidad: ningún club de la zona cobra siempre más
-- barato a las 15. Lo que sí hacen es sacar promociones con nombre, días, horario y fecha
-- de corte ("Promo mediodía, lunes a viernes de 12 a 17, hasta fin de septiembre").
--
-- La mecánica de resolución de precio es la misma, así que la tabla se renombra y se le
-- agrega la vigencia en vez de rehacerla: las franjas ya cargadas quedan como promociones
-- sin fecha de corte, que es exactamente lo que eran.
RENAME TABLE tarifas_cancha TO promociones_cancha;

ALTER TABLE promociones_cancha ADD COLUMN vigente_desde DATE NULL;
ALTER TABLE promociones_cancha ADD COLUMN vigente_hasta DATE NULL;

-- El listado público y la resolución de precio filtran por vigencia además de por cancha.
DROP INDEX idx_tarifa_cancha ON promociones_cancha;
CREATE INDEX idx_promocion_cancha ON promociones_cancha (cancha_id, activo, vigente_hasta);
