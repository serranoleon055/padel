-- Agrega seguimiento de sets para desempate en fase de grupos
ALTER TABLE posiciones_grupo
    ADD COLUMN sets_ganados INT NOT NULL DEFAULT 0 AFTER puntos,
    ADD COLUMN sets_perdidos INT NOT NULL DEFAULT 0 AFTER sets_ganados;
