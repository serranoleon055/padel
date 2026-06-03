-- Cupos maximos por categoria dentro de cada torneo.
CREATE TABLE torneo_cupos_categoria (
    torneo_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    cupo INT NOT NULL,
    PRIMARY KEY (torneo_id, categoria_id),
    CONSTRAINT fk_torneo_cupos_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id) ON DELETE CASCADE
);
