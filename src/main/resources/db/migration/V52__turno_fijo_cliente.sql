-- El abono se enlaza a la ficha del cliente.
--
-- Hasta acá la ficha buscaba los abonos con `clienteTelefono = telefono`, comparación
-- exacta de texto. Toda la identidad del cliente está construida sobre el teléfono
-- NORMALIZADO justamente para que "385-689-4061" y "3856894061" sean la misma persona,
-- pero los turnos fijos habían quedado fuera de esa regla: un abonado cargado con guiones
-- no mostraba su abono en la ficha.
--
-- Con la clave foránea el problema se cierra de raíz y deja de haber una cuarta copia de
-- las reglas de normalización dando vueltas.
ALTER TABLE turnos_fijos ADD COLUMN cliente_id BIGINT NULL;
ALTER TABLE turnos_fijos ADD CONSTRAINT fk_turno_fijo_cliente
    FOREIGN KEY (cliente_id) REFERENCES clientes(id);
CREATE INDEX idx_turno_fijo_cliente ON turnos_fijos (cliente_id);

-- ── Backfill ──────────────────────────────────────────────────────────────────
-- Mismos pasos que V43, en el mismo orden, sobre el teléfono del abono:
--   1. solo dígitos   2. sacar 54   3. sacar 9   4. sacar 0
--   5. si quedó de más de 10 dígitos, sacar el 15 posterior al código de área
--
-- El collation va explícito por lo mismo que en V43: sin él, comparar contra
-- clientes.telefono_normalizado tira "Illegal mix of collations".
CREATE TEMPORARY TABLE tmp_clientes_turno_fijo (
    turno_fijo_id BIGINT NOT NULL,
    tel_normalizado VARCHAR(20) COLLATE utf8mb4_unicode_ci NULL
);

INSERT INTO tmp_clientes_turno_fijo (turno_fijo_id, tel_normalizado)
SELECT
    t.id,
    CASE
        WHEN CHAR_LENGTH(paso4.tel) > 10
            THEN REGEXP_REPLACE(paso4.tel, '^([0-9]{2,4})15([0-9]{6,8})$', '$1$2')
        ELSE paso4.tel
    END
FROM turnos_fijos t
JOIN (
    SELECT
        t2.id,
        REGEXP_REPLACE(
            REGEXP_REPLACE(
                REGEXP_REPLACE(
                    REGEXP_REPLACE(COALESCE(t2.cliente_telefono, ''), '[^0-9]', ''),
                '^54', ''),
            '^9', ''),
        '^0', '') AS tel
    FROM turnos_fijos t2
) paso4 ON paso4.id = t.id;

UPDATE turnos_fijos t
    JOIN tmp_clientes_turno_fijo tmp ON tmp.turno_fijo_id = t.id
    JOIN clientes c ON c.telefono_normalizado = tmp.tel_normalizado
SET t.cliente_id = c.id;

DROP TEMPORARY TABLE tmp_clientes_turno_fijo;

-- Bloqueo optimista del stock.
--
-- Dos ventas simultáneas de la última unidad leían las dos stock = 1, las dos pasaban el
-- control y las dos escribían 0: se vendían dos unidades habiendo una, y el stock dejaba
-- de coincidir con la suma de sus movimientos. Con la versión, la segunda escritura falla
-- y el mostrador la reintenta con el número real.
ALTER TABLE productos ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
