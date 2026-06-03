CREATE TABLE plantillas_formato (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(500),
    formato_torneo VARCHAR(30) NOT NULL,
    tipo_sorteo VARCHAR(30) NOT NULL,
    cantidad_parejas_objetivo INT,
    cantidad_grupos INT,
    parejas_por_grupo INT,
    avanzan_por_grupo INT,
    incluye_fase_grupos BOOLEAN NOT NULL DEFAULT FALSE,
    incluye_eliminacion BOOLEAN NOT NULL DEFAULT TRUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE plantillas_puntos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(500),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE plantillas_puntos_rondas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_ronda VARCHAR(50) NOT NULL,
    puntos_ganador INT NOT NULL DEFAULT 0,
    puntos_perdedor INT NOT NULL DEFAULT 0,
    orden INT NOT NULL,
    plantilla_id BIGINT NOT NULL,
    CONSTRAINT fk_plantilla_puntos_ronda FOREIGN KEY (plantilla_id) REFERENCES plantillas_puntos(id)
);

ALTER TABLE torneos
    ADD COLUMN plantilla_formato_id BIGINT NULL AFTER activo,
    ADD COLUMN plantilla_formato_nombre VARCHAR(120) NULL AFTER plantilla_formato_id,
    ADD COLUMN plantilla_puntos_id BIGINT NULL AFTER plantilla_formato_nombre,
    ADD COLUMN plantilla_puntos_nombre VARCHAR(120) NULL AFTER plantilla_puntos_id,
    ADD COLUMN cantidad_parejas_objetivo INT NULL AFTER plantilla_puntos_nombre,
    ADD COLUMN parejas_por_grupo INT NULL AFTER cantidad_grupos,
    ADD COLUMN incluye_fase_grupos BOOLEAN NOT NULL DEFAULT FALSE AFTER avanzan_por_grupo,
    ADD COLUMN incluye_eliminacion BOOLEAN NOT NULL DEFAULT TRUE AFTER incluye_fase_grupos;

INSERT INTO plantillas_formato
    (nombre, descripcion, formato_torneo, tipo_sorteo, cantidad_parejas_objetivo, cantidad_grupos, parejas_por_grupo, avanzan_por_grupo, incluye_fase_grupos, incluye_eliminacion, activo)
VALUES
    ('11 parejas - grupos desparejos', 'Tres grupos desparejos para torneos con 11 parejas. Avanzan las mejores parejas y contempla byes en eliminacion.', 'MINITORNEO', 'ALEATORIO', 11, 3, 4, 2, TRUE, TRUE, TRUE),
    ('12 parejas - 4 grupos', 'Cuatro grupos de tres parejas, fase de grupos y eliminacion posterior.', 'MINITORNEO', 'ALEATORIO', 12, 4, 3, 2, TRUE, TRUE, TRUE),
    ('15 parejas - grupos con bye', 'Formato para 15 parejas con grupos desparejos y bye en el cuadro eliminatorio.', 'MINITORNEO', 'CABEZAS_SERIE', 15, 4, 4, 2, TRUE, TRUE, TRUE),
    ('16 parejas - cuadro directo', 'Eliminacion directa ideal para 16 parejas sin byes.', 'ELIMINACION_DIRECTA', 'CABEZAS_SERIE', 16, NULL, NULL, 1, FALSE, TRUE, TRUE);

INSERT INTO plantillas_puntos (nombre, descripcion, activo)
VALUES ('Ranking estandar', 'Puntos habituales para grupos, octavos, cuartos, semifinal y final.', TRUE);

INSERT INTO plantillas_puntos_rondas
    (plantilla_id, nombre_ronda, puntos_ganador, puntos_perdedor, orden)
SELECT id, 'Grupos', 10, 5, 1 FROM plantillas_puntos WHERE nombre = 'Ranking estandar';

INSERT INTO plantillas_puntos_rondas
    (plantilla_id, nombre_ronda, puntos_ganador, puntos_perdedor, orden)
SELECT id, 'Octavos', 20, 10, 2 FROM plantillas_puntos WHERE nombre = 'Ranking estandar';

INSERT INTO plantillas_puntos_rondas
    (plantilla_id, nombre_ronda, puntos_ganador, puntos_perdedor, orden)
SELECT id, 'Cuartos', 40, 20, 3 FROM plantillas_puntos WHERE nombre = 'Ranking estandar';

INSERT INTO plantillas_puntos_rondas
    (plantilla_id, nombre_ronda, puntos_ganador, puntos_perdedor, orden)
SELECT id, 'Semifinal', 70, 40, 4 FROM plantillas_puntos WHERE nombre = 'Ranking estandar';

INSERT INTO plantillas_puntos_rondas
    (plantilla_id, nombre_ronda, puntos_ganador, puntos_perdedor, orden)
SELECT id, 'Final', 100, 70, 5 FROM plantillas_puntos WHERE nombre = 'Ranking estandar';
