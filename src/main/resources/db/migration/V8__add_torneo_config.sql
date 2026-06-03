ALTER TABLE torneos
    ADD COLUMN cantidad_grupos INT NULL AFTER suma_puntos_ranking,
    ADD COLUMN avanzan_por_grupo INT NOT NULL DEFAULT 1 AFTER cantidad_grupos;
