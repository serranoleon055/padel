-- Los gastos pasan a tener jornada, anulación lógica y un flag de mercadería.
--
-- 1) JORNADA. El arqueo sumaba los ingresos por jornada (V55) y restaba los egresos por
--    día de calendario, porque `gastos` nunca tuvo la columna. En un club que cierra a
--    las 2, un pago al gasista en efectivo a las 00:30 se cargaba con fecha de "hoy" y no
--    se descontaba del efectivo esperado de la jornada que se estaba arqueando: faltaba
--    plata esa noche y sobraba la siguiente. Era el mismo bug que V55 arregló para los
--    cobros, en el otro lado de la resta.
--
--    `fecha` NO cambia de significado: sigue siendo la fecha contable que elige la
--    persona, y una factura de julio pagada en agosto sigue pesando en julio. `jornada`
--    es otra cosa: el día de caja en que la plata se movió. Se estampa al insertar y no
--    se recalcula nunca, igual que `cobros.jornada` y `reservas.precio_aplicado`.
--
--    El backfill usa `fecha` y no se deduce de `creado_en`, aunque sería más fiel: los
--    arqueos ya firmados se calcularon comparando contra `fecha`, así que cualquier otro
--    valor movería de día plata que ya está contada en un cierre con firma.
--
-- 2) ANULACIÓN. V51 hizo auditable la mitad de la plata: anular un cobro o una venta es
--    baja lógica y la fila queda. Los gastos se seguían borrando con DELETE, y los
--    egresos son la otra mitad del resultado que ve el dueño. Un gasto borrado no deja
--    nada que auditar: solo una línea de log.
--
-- 3) ES_MERCADERIA. El estado de resultados separa el gasto operativo de la compra de
--    mercadería preguntando si el gasto apunta a un producto. Eso funciona mientras la
--    compra sea de un producto por vez, pero una compra formal a proveedor tiene varios
--    productos en un solo comprobante y no puede apuntar a uno: sin el flag, toda compra
--    multiproducto se contaría como gasto operativo Y otra vez a través del costo de la
--    mercadería vendida, restando la misma plata dos veces. La columna entra ahora para
--    no volver a tocar la tabla después.

ALTER TABLE gastos ADD COLUMN jornada DATE NULL;
UPDATE gastos SET jornada = fecha WHERE jornada IS NULL;
ALTER TABLE gastos MODIFY COLUMN jornada DATE NOT NULL;

ALTER TABLE gastos ADD COLUMN anulado_en DATETIME NULL;
ALTER TABLE gastos ADD COLUMN anulado_por VARCHAR(120) NULL;
ALTER TABLE gastos ADD COLUMN motivo_anulacion VARCHAR(300) NULL;

ALTER TABLE gastos ADD COLUMN es_mercaderia TINYINT(1) NOT NULL DEFAULT 0;
UPDATE gastos SET es_mercaderia = 1 WHERE producto_id IS NOT NULL;

-- Los dos pares que usan todas las consultas de plata: la caja pregunta por jornada y el
-- estado de resultados por fecha, y las dos filtran los anulados.
CREATE INDEX idx_gastos_jornada ON gastos (jornada, anulado_en);
CREATE INDEX idx_gastos_fecha_anulado ON gastos (fecha, anulado_en);
