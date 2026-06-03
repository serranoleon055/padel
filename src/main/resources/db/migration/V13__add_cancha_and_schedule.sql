-- Tabla de canchas (pistas de pádel dentro de un lugar/club)
CREATE TABLE canchas (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255),
    activo TINYINT(1) NOT NULL DEFAULT 1,
    lugar_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cancha_lugar FOREIGN KEY (lugar_id) REFERENCES lugares(id)
);

-- Cantidad de canchas disponibles en un lugar
ALTER TABLE lugares
    ADD COLUMN cantidad_canchas INT DEFAULT NULL AFTER direccion;

-- Programación previa de partidos: fecha/hora y cancha asignada
-- (fecha_hora sigue usándose como timestamp del resultado real)
ALTER TABLE partidos
    ADD COLUMN fecha_hora_programada DATETIME DEFAULT NULL AFTER fecha_hora,
    ADD COLUMN cancha_id BIGINT DEFAULT NULL AFTER fecha_hora_programada,
    ADD CONSTRAINT fk_partido_cancha FOREIGN KEY (cancha_id) REFERENCES canchas(id);
