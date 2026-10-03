-- Compras formales a proveedor, con cuenta corriente.
--
-- Hasta acá la compra era de a UN producto: `POST /api/productos/{id}/compras`. Un remito
-- de doce productos eran doce llamadas y, peor, doce gastos separados en la rentabilidad
-- del mes, imposibles de reconciliar contra el papel que el club tiene en la mano. Y el
-- proveedor del gasto se guardaba como TEXTO libre, así que ni siquiera se podía agrupar
-- sin ambigüedad.
--
-- Ahora la compra es un documento: proveedor, tipo y número de comprobante, fecha, y
-- varios productos en una sola carga. Genera UN `Gasto` y N `MovimientoStock`, los tres
-- enlazados, y se puede revertir entera.
--
-- DECISIÓN: la compra NO tiene tabla de renglones propia. El renglón de la compra ES el
-- movimiento de stock, enlazado por `documento_compra_id`. Duplicarlo en una tabla aparte
-- es garantizar que algún día las dos no coincidan, y rompería el invariante de V48: el
-- stock de un producto tiene que ser siempre la suma de sus movimientos.
--
-- CUENTA CORRIENTE: una compra sin `medio` es a crédito —todavía no se pagó—. El egreso
-- igual se registra, porque la mercadería ya entró y pesa en la rentabilidad del mes
-- (criterio devengado, igual que el resto de Estadísticas); lo que no hace es salir del
-- cajón. Los pagos al proveedor van en `pagos_proveedor` y ESOS sí mueven la caja. El
-- saldo es la suma de las compras a crédito menos los pagos.
--
-- Por eso `gastos.medio` pasa a aceptar NULL: hasta acá todo gasto estaba pagado en el
-- momento. Y la caja deja de contar los gastos sin medio en el total del día, porque un
-- egreso que todavía no salió del cajón no es plata que salió: es la misma distinción
-- entre devengado y caja que ya está escrita para los ingresos.

ALTER TABLE gastos MODIFY COLUMN medio VARCHAR(20) NULL;

-- El proveedor como FK. Se conserva la columna de texto: es lo que alguien escribió a
-- mano y el match por nombre no es confiable, así que no se borra ni se "corrige".
ALTER TABLE gastos ADD COLUMN proveedor_id BIGINT NULL;
ALTER TABLE gastos
    ADD CONSTRAINT fk_gasto_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id);

UPDATE gastos g
    JOIN proveedores p ON p.nombre = g.proveedor
SET g.proveedor_id = p.id
WHERE g.proveedor_id IS NULL;

CREATE TABLE documentos_compra (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    proveedor_id BIGINT NOT NULL,
    -- FACTURA_A | FACTURA_B | FACTURA_C | REMITO | TICKET | OTRO
    tipo_comprobante VARCHAR(20) NOT NULL,
    numero VARCHAR(40) NOT NULL,
    -- Fecha del comprobante: la contable, la que elige la persona.
    fecha DATE NOT NULL,
    -- Jornada de caja en la que se cargó. Igual que cobros, ventas y gastos (V55/V58).
    jornada DATE NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    -- NULL = a cuenta corriente, todavía sin pagar.
    medio VARCHAR(20) NULL,
    gasto_id BIGINT NULL,
    registrado_por VARCHAR(80) NULL,
    notas VARCHAR(300) NULL,
    anulado_en DATETIME NULL,
    anulado_por VARCHAR(120) NULL,
    motivo_anulacion VARCHAR(300) NULL,
    creado_en DATETIME NOT NULL,
    -- Dos remitos con el mismo número del mismo proveedor son el mismo papel cargado dos
    -- veces, que es el error típico de carga. El único mira solo los vigentes: anulada
    -- una compra, el número se puede volver a usar. Es la misma columna generada de V59,
    -- por la misma razón: MySQL trata cada NULL como distinto.
    numero_vigente VARCHAR(40) GENERATED ALWAYS AS (IF(anulado_en IS NULL, numero, NULL)) STORED,
    CONSTRAINT fk_compra_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id),
    CONSTRAINT fk_compra_gasto FOREIGN KEY (gasto_id) REFERENCES gastos(id),
    CONSTRAINT uq_compra_numero UNIQUE (proveedor_id, tipo_comprobante, numero_vigente)
);

CREATE INDEX idx_compra_proveedor_fecha ON documentos_compra (proveedor_id, fecha);
CREATE INDEX idx_compra_jornada ON documentos_compra (jornada, anulado_en);

ALTER TABLE gastos ADD COLUMN documento_compra_id BIGINT NULL;
ALTER TABLE gastos
    ADD CONSTRAINT fk_gasto_compra FOREIGN KEY (documento_compra_id) REFERENCES documentos_compra(id);

-- El renglón de la compra: a qué documento pertenece cada entrada de mercadería.
ALTER TABLE movimientos_stock ADD COLUMN documento_compra_id BIGINT NULL;
ALTER TABLE movimientos_stock
    ADD CONSTRAINT fk_movstock_compra FOREIGN KEY (documento_compra_id) REFERENCES documentos_compra(id);
CREATE INDEX idx_movstock_compra ON movimientos_stock (documento_compra_id);

-- Lo que se le paga al proveedor. Esto SÍ sale del cajón; la compra a crédito no.
CREATE TABLE pagos_proveedor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    proveedor_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    jornada DATE NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    medio VARCHAR(20) NOT NULL,
    notas VARCHAR(300) NULL,
    registrado_por VARCHAR(80) NULL,
    anulado_en DATETIME NULL,
    anulado_por VARCHAR(120) NULL,
    motivo_anulacion VARCHAR(300) NULL,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_pagoprov_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id)
);

CREATE INDEX idx_pagoprov_proveedor ON pagos_proveedor (proveedor_id, fecha);
CREATE INDEX idx_pagoprov_jornada ON pagos_proveedor (jornada, anulado_en);
