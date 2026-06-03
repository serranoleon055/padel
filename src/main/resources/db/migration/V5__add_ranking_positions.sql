ALTER TABLE ranking_entries
    ADD COLUMN posicion_actual INT NOT NULL DEFAULT 0,
    ADD COLUMN posicion_anterior INT NOT NULL DEFAULT 0;
