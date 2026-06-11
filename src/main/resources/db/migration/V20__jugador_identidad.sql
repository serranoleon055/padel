ALTER TABLE jugadores
    ADD COLUMN telefono VARCHAR(40) NULL,
    ADD COLUMN fecha_nacimiento DATE NULL,
    ADD COLUMN nombre_normalizado VARCHAR(180) NULL;

UPDATE jugadores SET nombre_normalizado = LOWER(TRIM(CONCAT(nombre, ' ', apellido)));

CREATE INDEX idx_jugador_nombre_normalizado ON jugadores (nombre_normalizado);
