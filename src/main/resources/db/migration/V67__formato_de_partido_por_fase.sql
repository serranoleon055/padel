-- Formato de partido por fase: la zona a un set y las semifinales y la final a 9 games,
-- o la llave con super tie-break. NULL = hereda (la zona y la llave toman mejor_de_sets;
-- las finales, el de la llave), así que los torneos existentes se leen igual que antes.
ALTER TABLE torneo_categoria_config
    ADD COLUMN formato_partido_grupos VARCHAR(40) NULL,
    ADD COLUMN formato_partido_eliminacion VARCHAR(40) NULL,
    ADD COLUMN formato_partido_finales VARCHAR(40) NULL;
