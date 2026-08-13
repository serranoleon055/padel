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
--
-- El índice nuevo se crea ANTES de borrar el viejo, y el orden importa: `idx_tarifa_cancha`
-- es el único índice que cubre la clave foránea `fk_tarifa_cancha`, y MySQL no deja borrar
-- el último índice que sostiene una FK. Creando primero el reemplazo —que también arranca
-- por cancha_id— la FK nunca se queda sin índice y el borrado pasa siempre.
CREATE INDEX idx_promocion_cancha ON promociones_cancha (cancha_id, activo, vigente_hasta);
DROP INDEX idx_tarifa_cancha ON promociones_cancha;
