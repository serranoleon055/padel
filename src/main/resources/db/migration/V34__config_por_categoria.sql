CREATE TABLE torneo_categoria_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    formato VARCHAR(30),
    plantilla_formato_id BIGINT,
    plantilla_formato_nombre VARCHAR(120),
    plantilla_puntos_id BIGINT,
    plantilla_puntos_nombre VARCHAR(120),
    cantidad_parejas_objetivo INT,
    cantidad_grupos INT,
    parejas_por_grupo INT,
    avanzan_por_grupo INT,
    incluye_fase_grupos BOOLEAN NOT NULL DEFAULT FALSE,
    incluye_eliminacion BOOLEAN NOT NULL DEFAULT TRUE,
    tipo_sorteo VARCHAR(30),
    mejor_de_sets INT NOT NULL DEFAULT 3,
    cupo INT,
    CONSTRAINT fk_tcc_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id) ON DELETE CASCADE,
    CONSTRAINT fk_tcc_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT uq_tcc_torneo_categoria UNIQUE (torneo_id, categoria_id)
);

INSERT INTO torneo_categoria_config
    (torneo_id, categoria_id, formato, plantilla_formato_id, plantilla_formato_nombre,
     plantilla_puntos_id, plantilla_puntos_nombre, cantidad_parejas_objetivo, cantidad_grupos,
     parejas_por_grupo, avanzan_por_grupo, incluye_fase_grupos, incluye_eliminacion,
     tipo_sorteo, mejor_de_sets, cupo)
SELECT t.id, tc.categoria_id, t.formato, t.plantilla_formato_id, t.plantilla_formato_nombre,
       t.plantilla_puntos_id, t.plantilla_puntos_nombre, t.cantidad_parejas_objetivo, t.cantidad_grupos,
       t.parejas_por_grupo, t.avanzan_por_grupo, t.incluye_fase_grupos, t.incluye_eliminacion,
       t.tipo_sorteo, t.mejor_de_sets, COALESCE(cc.cupo, t.cupo_maximo_parejas)
FROM torneos t
JOIN torneo_categorias tc ON tc.torneo_id = t.id
LEFT JOIN torneo_cupos_categoria cc ON cc.torneo_id = t.id AND cc.categoria_id = tc.categoria_id;

ALTER TABLE configuracion_puntos
    ADD COLUMN categoria_id BIGINT NULL AFTER torneo_id,
    ADD CONSTRAINT fk_config_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id);

CREATE TEMPORARY TABLE tmp_config_puntos AS
    SELECT cp.nombre_ronda, cp.puntos_ganador, cp.puntos_perdedor, cp.orden, cp.torneo_id,
           tc.categoria_id AS categoria_id
    FROM configuracion_puntos cp
    JOIN torneo_categorias tc ON tc.torneo_id = cp.torneo_id
    WHERE cp.categoria_id IS NULL;

DELETE FROM configuracion_puntos
WHERE categoria_id IS NULL
  AND torneo_id IN (SELECT DISTINCT torneo_id FROM torneo_categorias);

INSERT INTO configuracion_puntos
    (nombre_ronda, puntos_ganador, puntos_perdedor, orden, torneo_id, categoria_id)
SELECT nombre_ronda, puntos_ganador, puntos_perdedor, orden, torneo_id, categoria_id
FROM tmp_config_puntos;

DROP TEMPORARY TABLE tmp_config_puntos;
