-- Agrega seguimiento de juegos (games) para el tercer criterio de desempate en fase de grupos
-- Orden de desempate en pádel: 1) puntos, 2) partido directo*, 3) diff sets, 4) diff juegos
-- *El desempate por partido directo se resuelve en lógica de aplicación
ALTER TABLE posiciones_grupo
    ADD COLUMN juegos_ganados INT NOT NULL DEFAULT 0 AFTER sets_perdidos,
    ADD COLUMN juegos_perdidos INT NOT NULL DEFAULT 0 AFTER juegos_ganados;
