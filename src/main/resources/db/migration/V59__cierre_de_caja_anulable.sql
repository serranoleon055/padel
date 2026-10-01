-- Reabrir un cierre dejaba de existir el arqueo firmado.
--
-- `CajaService.reabrir` hacía DELETE de la fila: después de reabrir no quedaba en la base
-- quién había contado cuánto ni con qué diferencia, solo una línea de log. Es justo lo
-- contrario de lo que V51 vino a resolver, y el arqueo es el registro más sensible que
-- tiene el sistema: es el único lugar donde consta que a alguien le faltó plata.
--
-- Pasa a ser baja lógica. Y eso obliga a cambiar el índice: con la fila anulada todavía
-- en la tabla, volver a cerrar esa jornada chocaría contra `uk_cierres_caja_fecha`.
--
-- No sirve un UNIQUE (fecha, anulado_en): MySQL trata cada NULL como distinto, así que
-- dos arqueos VIGENTES de la misma jornada pasarían el control, que es exactamente lo que
-- no puede pasar. La columna generada deja ver la fecha solo mientras el arqueo está
-- vigente, así conviven N anulados y como máximo uno en pie.
--
-- El índice nuevo se crea ANTES de borrar el viejo, siempre: un DROP INDEX sobre el único
-- índice que sostiene una restricción pasa en el MySQL local y falla en el gestionado.

ALTER TABLE cierres_caja ADD COLUMN anulado_en DATETIME NULL;
ALTER TABLE cierres_caja ADD COLUMN anulado_por VARCHAR(120) NULL;
ALTER TABLE cierres_caja ADD COLUMN motivo_reapertura VARCHAR(300) NULL;

ALTER TABLE cierres_caja
    ADD COLUMN fecha_vigente DATE
        GENERATED ALWAYS AS (IF(anulado_en IS NULL, fecha, NULL)) STORED;

CREATE UNIQUE INDEX uk_cierres_caja_vigente ON cierres_caja (fecha_vigente);
DROP INDEX uk_cierres_caja_fecha ON cierres_caja;

-- El historial de arqueos se lee por fecha descendente, anulados incluidos.
CREATE INDEX idx_cierres_caja_fecha ON cierres_caja (fecha);
