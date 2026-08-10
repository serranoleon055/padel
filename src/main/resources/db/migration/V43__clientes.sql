-- Ficha de cliente unificada. Hasta ahora cada reserva guardaba nombre y teléfono como
-- texto libre: no había forma de saber que el "Juan Perez" que reservó ocho veces era
-- el mismo, ni de armar historial, gasto acumulado o ausencias por persona.
--
-- La identidad es el teléfono normalizado (solo dígitos, sin 54/9/0 y sin el 15 que va
-- después del código de área). Mismas reglas que NormalizadorTelefono.java y whatsapp.ts.
-- El collation de telefono_normalizado va explícito: las tablas viejas quedaron en
-- utf8mb4_spanish_ci y las nuevas heredan el default de la base (que cambia según cómo
-- se haya creado). Sin esto, comparar contra reservas.cliente_telefono tira
-- "Illegal mix of collations". Son solo dígitos, así que el collation da igual: lo que
-- importa es que sea el mismo en los dos lados de cada comparación.
CREATE TABLE clientes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    telefono VARCHAR(40) NOT NULL,
    telefono_normalizado VARCHAR(20) COLLATE utf8mb4_unicode_ci NOT NULL,
    email VARCHAR(120) NULL,
    notas VARCHAR(500) NULL,
    creado_en DATETIME NOT NULL,
    CONSTRAINT uq_cliente_telefono UNIQUE (telefono_normalizado)
);

ALTER TABLE reservas ADD COLUMN cliente_id BIGINT NULL;
ALTER TABLE reservas ADD CONSTRAINT fk_reserva_cliente
    FOREIGN KEY (cliente_id) REFERENCES clientes(id);
CREATE INDEX idx_reserva_cliente ON reservas (cliente_id, fecha);

-- Un cliente puede además ser jugador de torneos: se enlazan, no se duplican.
ALTER TABLE jugadores ADD COLUMN cliente_id BIGINT NULL;
ALTER TABLE jugadores ADD CONSTRAINT fk_jugador_cliente
    FOREIGN KEY (cliente_id) REFERENCES clientes(id);

-- ── Backfill ──────────────────────────────────────────────────────────────────
-- Forma canónica del teléfono, paso a paso (equivalente a NormalizadorTelefono):
--   1. solo dígitos   2. sacar 54   3. sacar 9   4. sacar 0
--   5. si quedó de más de 10 dígitos, sacar el 15 posterior al código de área
CREATE TEMPORARY TABLE tmp_clientes_reserva (
    reserva_id BIGINT NOT NULL,
    cliente_nombre VARCHAR(120) NOT NULL,
    cliente_telefono VARCHAR(40) NOT NULL,
    tel_normalizado VARCHAR(20) COLLATE utf8mb4_unicode_ci NULL
);

INSERT INTO tmp_clientes_reserva (reserva_id, cliente_nombre, cliente_telefono, tel_normalizado)
SELECT
    r.id,
    r.cliente_nombre,
    r.cliente_telefono,
    CASE
        WHEN CHAR_LENGTH(paso4.tel) > 10
            THEN REGEXP_REPLACE(paso4.tel, '^([0-9]{2,4})15([0-9]{6,8})$', '$1$2')
        ELSE paso4.tel
    END
FROM reservas r
JOIN (
    SELECT
        r2.id,
        REGEXP_REPLACE(
            REGEXP_REPLACE(
                REGEXP_REPLACE(
                    REGEXP_REPLACE(r2.cliente_telefono, '[^0-9]', ''),
                '^54', ''),
            '^9', ''),
        '^0', '') AS tel
    FROM reservas r2
) paso4 ON paso4.id = r.id;

-- Un cliente por teléfono; se toma el nombre de la reserva más reciente.
INSERT INTO clientes (nombre, telefono, telefono_normalizado, creado_en)
SELECT
    SUBSTRING_INDEX(GROUP_CONCAT(t.cliente_nombre ORDER BY t.reserva_id DESC SEPARATOR '\n'), '\n', 1),
    SUBSTRING_INDEX(GROUP_CONCAT(t.cliente_telefono ORDER BY t.reserva_id DESC SEPARATOR '\n'), '\n', 1),
    t.tel_normalizado,
    NOW()
FROM tmp_clientes_reserva t
WHERE CHAR_LENGTH(t.tel_normalizado) >= 8
GROUP BY t.tel_normalizado;

UPDATE reservas r
    JOIN tmp_clientes_reserva t ON t.reserva_id = r.id
    JOIN clientes c ON c.telefono_normalizado = t.tel_normalizado
SET r.cliente_id = c.id;

DROP TEMPORARY TABLE tmp_clientes_reserva;

-- Los jugadores con teléfono cargado que coincidan con un cliente quedan enlazados.
CREATE TEMPORARY TABLE tmp_clientes_jugador (
    jugador_id BIGINT NOT NULL,
    tel_normalizado VARCHAR(20) COLLATE utf8mb4_unicode_ci NULL
);

INSERT INTO tmp_clientes_jugador (jugador_id, tel_normalizado)
SELECT
    j.id,
    CASE
        WHEN CHAR_LENGTH(paso4.tel) > 10
            THEN REGEXP_REPLACE(paso4.tel, '^([0-9]{2,4})15([0-9]{6,8})$', '$1$2')
        ELSE paso4.tel
    END
FROM jugadores j
JOIN (
    SELECT
        j2.id,
        REGEXP_REPLACE(
            REGEXP_REPLACE(
                REGEXP_REPLACE(
                    REGEXP_REPLACE(COALESCE(j2.telefono, ''), '[^0-9]', ''),
                '^54', ''),
            '^9', ''),
        '^0', '') AS tel
    FROM jugadores j2
) paso4 ON paso4.id = j.id;

UPDATE jugadores j
    JOIN tmp_clientes_jugador t ON t.jugador_id = j.id
    JOIN clientes c ON c.telefono_normalizado = t.tel_normalizado
SET j.cliente_id = c.id;

DROP TEMPORARY TABLE tmp_clientes_jugador;
