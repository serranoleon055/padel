-- Un turno pasa a ser UNA fila con su duración real (60 / 90 / 120), en vez de N
-- reservas de una hora pegadas. Antes, un turno de 2 h eran dos filas: se contaba
-- doble en estadísticas, se cobraba en dos pasos y no había forma de vender 90 min.
--
-- La garantía de "no vender dos veces el mismo horario" la daba el índice único sobre
-- reservas.clave_slot. Con duración libre una sola clave ya no alcanza (un turno de 90
-- desde las 18 ocupa 18:00, 18:30 y 19:00), así que pasa a una tabla hija con una fila
-- por bloque de 30 minutos ocupado. La granularidad de 30 es el máximo común divisor de
-- las duraciones que se venden.

-- ---------------------------------------------------------------------------
-- 1. Duración real de cada reserva, derivada de lo que ya estaba guardado.
-- ---------------------------------------------------------------------------
ALTER TABLE reservas ADD COLUMN duracion_min INT NOT NULL DEFAULT 60;

UPDATE reservas
SET duracion_min = CASE
        WHEN hora_fin > hora_inicio THEN
            TIMESTAMPDIFF(MINUTE, CONCAT('2000-01-01 ', hora_inicio), CONCAT('2000-01-01 ', hora_fin))
        ELSE
            TIMESTAMPDIFF(MINUTE, CONCAT('2000-01-01 ', hora_inicio), CONCAT('2000-01-02 ', hora_fin))
    END;

-- ---------------------------------------------------------------------------
-- 2. Fusionar los lotes históricos: las reservas contiguas del mismo cliente, en la
--    misma cancha y día, con el mismo estado, el mismo pago y el mismo abono, eran un
--    solo turno partido en pedazos. Se conserva la primera y absorbe a las demás.
-- ---------------------------------------------------------------------------
CREATE TEMPORARY TABLE tmp_lotes AS
WITH marcado AS (
    SELECT
        id, cancha_id, fecha, cliente_telefono, estado, hora_inicio, hora_fin,
        duracion_min, precio_aplicado,
        CASE WHEN LAG(hora_fin)          OVER w = hora_inicio
              AND LAG(cancha_id)         OVER w = cancha_id
              AND LAG(fecha)             OVER w = fecha
              AND LAG(cliente_telefono)  OVER w = cliente_telefono
              AND LAG(estado)            OVER w = estado
              AND COALESCE(LAG(pago_id)       OVER w, -1) = COALESCE(pago_id, -1)
              AND COALESCE(LAG(turno_fijo_id) OVER w, -1) = COALESCE(turno_fijo_id, -1)
             THEN 0 ELSE 1
        END AS nueva_isla
    FROM reservas
    WINDOW w AS (ORDER BY cancha_id, fecha, cliente_telefono, estado, hora_inicio)
),
islas AS (
    SELECT marcado.*,
           SUM(nueva_isla) OVER (ORDER BY cancha_id, fecha, cliente_telefono, estado, hora_inicio
                                 ROWS UNBOUNDED PRECEDING) AS isla
    FROM marcado
)
SELECT
    isla,
    MIN(id)                AS id_sobreviviente,
    SUM(duracion_min)      AS duracion_total,
    SUM(precio_aplicado)   AS precio_total,
    COUNT(*)               AS piezas,
    SUBSTRING_INDEX(GROUP_CONCAT(hora_fin ORDER BY hora_inicio), ',', -1) AS hora_fin_final
FROM islas
GROUP BY isla
HAVING COUNT(*) > 1;

CREATE TEMPORARY TABLE tmp_absorbidas AS
SELECT r.id AS id_absorbida, l.id_sobreviviente
FROM reservas r
JOIN tmp_lotes l ON r.id > l.id_sobreviviente
JOIN reservas s ON s.id = l.id_sobreviviente
WHERE r.cancha_id = s.cancha_id
  AND r.fecha = s.fecha
  AND r.cliente_telefono = s.cliente_telefono
  AND r.estado = s.estado
  AND COALESCE(r.pago_id, -1) = COALESCE(s.pago_id, -1)
  AND COALESCE(r.turno_fijo_id, -1) = COALESCE(s.turno_fijo_id, -1)
  AND r.hora_inicio >= s.hora_inicio
  AND r.hora_inicio < ADDTIME(s.hora_inicio, SEC_TO_TIME(l.duracion_total * 60));

-- La primera fila del lote se queda con el turno entero.
UPDATE reservas r
JOIN tmp_lotes l ON l.id_sobreviviente = r.id
SET r.duracion_min    = l.duracion_total,
    r.hora_fin        = l.hora_fin_final,
    r.precio_aplicado = l.precio_total;

-- Los cobros del mostrador apuntaban a cada pedazo: ahora van todos al turno.
UPDATE cobros c
JOIN tmp_absorbidas a ON a.id_absorbida = c.reserva_id
SET c.reserva_id = a.id_sobreviviente;

DELETE r FROM reservas r JOIN tmp_absorbidas a ON a.id_absorbida = r.id;

DROP TEMPORARY TABLE tmp_absorbidas;
DROP TEMPORARY TABLE tmp_lotes;

-- ---------------------------------------------------------------------------
-- 3. Los horarios ocupados pasan a una tabla hija: una fila por bloque de 30 min.
--    El índice único es el que impide vender dos veces la misma cancha a la misma hora.
-- ---------------------------------------------------------------------------
CREATE TABLE reserva_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reserva_id BIGINT NOT NULL,
    clave_slot VARCHAR(80) NOT NULL,
    CONSTRAINT uq_reserva_slot UNIQUE (clave_slot),
    CONSTRAINT fk_reserva_slot_reserva FOREIGN KEY (reserva_id) REFERENCES reservas(id) ON DELETE CASCADE,
    INDEX idx_reserva_slot_reserva (reserva_id)
);

-- Se pueblan solo las reservas que tenían el horario tomado (clave_slot no nula: las
-- canceladas, rechazadas y expiradas ya lo habían soltado). Hasta 4 bloques de 30 min
-- cubren el turno más largo que el sistema permite.
INSERT INTO reserva_slots (reserva_id, clave_slot)
SELECT r.id,
       CONCAT(r.cancha_id, '|', r.fecha, '|',
              DATE_FORMAT(ADDTIME(r.hora_inicio, SEC_TO_TIME(bloque.n * 1800)), '%H:%i'))
FROM reservas r
JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) AS bloque
  ON bloque.n * 30 < r.duracion_min
WHERE r.clave_slot IS NOT NULL;

ALTER TABLE reservas DROP INDEX uq_reserva_slot_activo;
ALTER TABLE reservas DROP COLUMN clave_slot;

-- ---------------------------------------------------------------------------
-- 4. Qué duraciones vende el club. Antes solo se podía vender la duración del slot
--    (una hora) o pegar varias; ahora es una decisión explícita por sucursal.
--    El paso de la grilla se deriva del máximo común divisor de estas duraciones,
--    así que ofrecer 90 min habilita solo los inicios que hacen falta.
-- ---------------------------------------------------------------------------
ALTER TABLE horarios_cancha ADD COLUMN duraciones_ofrecidas VARCHAR(40) NOT NULL DEFAULT '60,120';
ALTER TABLE horarios_cancha DROP COLUMN duracion_slot_min;

-- ---------------------------------------------------------------------------
-- 5. Los abonos también pasan a duración, en vez de contar slots de una hora.
-- ---------------------------------------------------------------------------
ALTER TABLE turnos_fijos ADD COLUMN duracion_min INT NOT NULL DEFAULT 60;
UPDATE turnos_fijos SET duracion_min = GREATEST(60, slots * 60);
ALTER TABLE turnos_fijos DROP COLUMN slots;
