CREATE TABLE campeones_torneo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    pareja_campeona_id BIGINT NOT NULL,
    pareja_subcampeona_id BIGINT NULL,
    marcador_final VARCHAR(60) NULL,
    fecha_coronacion DATETIME NULL,
    CONSTRAINT fk_campeon_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id),
    CONSTRAINT fk_campeon_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT fk_campeon_pareja FOREIGN KEY (pareja_campeona_id) REFERENCES parejas(id),
    CONSTRAINT fk_campeon_subpareja FOREIGN KEY (pareja_subcampeona_id) REFERENCES parejas(id)
);
