-- Permite que columnas de configuracion de torneo sean NULL
-- (necesario para formato LIGA que no usa eliminatorias ni grupos configurados)
ALTER TABLE torneos
    MODIFY COLUMN avanzan_por_grupo INT DEFAULT NULL,
    MODIFY COLUMN parejas_por_grupo INT DEFAULT NULL;
