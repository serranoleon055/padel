-- Rastro de auditoría de resultados: qué admin cargó (o corrigió/declaró WO)
-- el resultado de un partido y cuándo. Clave para resolver disputas de ranking.

ALTER TABLE partidos
    ADD COLUMN resultado_cargado_por VARCHAR(50) NULL,
    ADD COLUMN resultado_cargado_en DATETIME NULL;
