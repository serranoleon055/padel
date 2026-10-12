-- Reglas para armar el fixture: cuánto dura el turno de cada partido (por categoría),
-- el descanso mínimo entre partidos de una pareja, y si una categoría se juega toda en
-- un club. Todo opcional: lo que queda en NULL toma el valor sugerido.
ALTER TABLE torneos ADD COLUMN descanso_minimo_minutos INT NULL;

ALTER TABLE torneo_categoria_config
    ADD COLUMN duracion_partido_minutos INT NULL,
    ADD COLUMN lugar_fijo_id BIGINT NULL;

ALTER TABLE torneo_categoria_config
    ADD CONSTRAINT fk_config_lugar_fijo FOREIGN KEY (lugar_fijo_id) REFERENCES lugares (id);
