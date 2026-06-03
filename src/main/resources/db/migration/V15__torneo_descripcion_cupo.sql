-- Descripcion e imagen para la vista publica del torneo
-- Cupo maximo de parejas por torneo (control de capacidad)
ALTER TABLE torneos
    ADD COLUMN descripcion TEXT DEFAULT NULL AFTER nombre,
    ADD COLUMN imagen_url VARCHAR(500) DEFAULT NULL AFTER descripcion,
    ADD COLUMN cupo_maximo_parejas INT DEFAULT NULL AFTER imagen_url;
