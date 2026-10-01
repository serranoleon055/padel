-- El costo de inscripción se congela en la solicitud.
--
-- Las estadísticas calculaban los ingresos por inscripción con el costo VIVO del torneo,
-- leído en el momento de armar el panel. Si el club cambiaba el costo de inscripción,
-- cambiaba la facturación de todos los meses pasados: el mismo torneo del mes de julio
-- valía distinto según el día en que se mirara el informe. Es el bug que
-- `reservas.precio_aplicado` (V41) y `venta_items.precio_unitario` (V48) existen para
-- evitar, en el único lugar donde faltaba aplicarlo.
--
-- Lo que NO cambia es el criterio: las estadísticas siguen siendo devengadas y cuentan la
-- inscripción aprobada aunque todavía no se haya cobrado. Caja es de caja y Estadísticas
-- es devengado; los dos números están bien y no coinciden a propósito.
--
-- El backfill usa el costo actual del torneo porque es el único dato que existe. A partir
-- de acá se estampa al aprobar la solicitud y no se mueve más.
--
-- Se guarda el costo POR JUGADOR, no el total de la pareja. Hoy una solicitud tiene
-- exactamente dos integrantes, así que el total es el doble y da lo mismo; guardar el
-- precio unitario es lo que hace que el número siga estando bien el día que un torneo
-- tenga equipos de otro tamaño, sin otra migración y sin reescribir el histórico.

ALTER TABLE solicitudes_inscripcion ADD COLUMN costo_aplicado DECIMAL(12,2) NULL;

UPDATE solicitudes_inscripcion s
    JOIN torneos t ON t.id = s.torneo_id
SET s.costo_aplicado = t.costo_inscripcion_jugador
WHERE s.costo_aplicado IS NULL;
