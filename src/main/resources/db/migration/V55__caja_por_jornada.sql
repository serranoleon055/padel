-- La caja pasa a cerrar por JORNADA, no por día de calendario.
--
-- El club abre a las 10 y cierra a las 2 de la madrugada. Hasta acá, la caja cortaba a
-- medianoche: todo lo que se cobraba entre las 00:00 y el cierre caía en el día
-- siguiente. El que atiende arqueaba a las 2:15, contaba la plata FÍSICA del cajón (que
-- incluye las dos últimas horas de la noche) y firmaba un cierre al que le faltaban
-- justamente esas dos horas. La diferencia del arqueo —el único número que al dueño le
-- importa— salía mal todas las noches, y el cierre firmado de V51 perdía sentido en el
-- club que más lo necesita: el que trabaja de noche, o sea, todos.
--
-- La jornada se estampa al insertar y NO se recalcula nunca. Es la misma decisión que
-- `reservas.precio_aplicado` y que los totales congelados del arqueo: si mañana el club
-- cambia su horario de apertura, la plata no puede cambiarse de día sola en un cierre ya
-- firmado.

ALTER TABLE cobros ADD COLUMN jornada DATE NULL;
ALTER TABLE ventas ADD COLUMN jornada DATE NULL;
-- En pagos queda NULL a propósito: un pago pendiente todavía no entró a ninguna caja.
-- Se completa cuando se aprueba.
ALTER TABLE pagos  ADD COLUMN jornada DATE NULL;

-- Backfill: para lo ya cargado se deduce de la hora contra la apertura más temprana del
-- club. Es una aproximación y alcanza, porque entre el cierre y la apertura no hay nadie
-- cobrando: lo anotado de madrugada es, siempre, de la noche que arrancó el día anterior.
-- Si no hay ningún horario cargado, la apertura es 00:00 y nada se mueve de día.
UPDATE cobros
SET jornada = CASE
        WHEN cobrado_en IS NULL THEN CURDATE()
        WHEN TIME(cobrado_en) < (SELECT COALESCE(MIN(hora_apertura), '00:00:00')
                                 FROM horarios_cancha WHERE activo = 1)
            THEN DATE_SUB(DATE(cobrado_en), INTERVAL 1 DAY)
        ELSE DATE(cobrado_en)
    END;

UPDATE ventas
SET jornada = CASE
        WHEN fecha IS NULL THEN CURDATE()
        WHEN TIME(fecha) < (SELECT COALESCE(MIN(hora_apertura), '00:00:00')
                            FROM horarios_cancha WHERE activo = 1)
            THEN DATE_SUB(DATE(fecha), INTERVAL 1 DAY)
        ELSE DATE(fecha)
    END;

UPDATE pagos
SET jornada = CASE
        WHEN TIME(pagado_en) < (SELECT COALESCE(MIN(hora_apertura), '00:00:00')
                                FROM horarios_cancha WHERE activo = 1)
            THEN DATE_SUB(DATE(pagado_en), INTERVAL 1 DAY)
        ELSE DATE(pagado_en)
    END
WHERE pagado_en IS NOT NULL;

-- Un cobro o una venta sin jornada no aparecería en ninguna caja: es plata que se pierde
-- de vista. Por eso acá sí es obligatoria.
ALTER TABLE cobros MODIFY COLUMN jornada DATE NOT NULL;
ALTER TABLE ventas MODIFY COLUMN jornada DATE NOT NULL;

-- La caja busca siempre por jornada + no anulado, que es el par que se usa en todas las
-- consultas de arqueo.
CREATE INDEX idx_cobros_jornada ON cobros (jornada, anulado_en);
CREATE INDEX idx_ventas_jornada ON ventas (jornada, anulado_en);
CREATE INDEX idx_pagos_jornada ON pagos (jornada, estado);
