-- Sponsors del club en las páginas públicas.
--
-- Es la función que le permite al club GANAR plata con el sistema en vez de solo pagarlo:
-- el espacio del cuadro del torneo y de la página de turnos se le vende a la cervecería
-- de la esquina, y con eso se cubre el abono. Los dos competidores del rubro lo tienen, y
-- no por casualidad.
CREATE TABLE sponsors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    -- URL de Cloudinary, subida por el mismo camino que las fotos de la galería.
    logo_url VARCHAR(500) NOT NULL,
    -- A dónde lleva el logo. Opcional: no todo sponsor tiene web.
    enlace VARCHAR(500) NULL,
    -- Null = se muestra en todas las sedes. Con lugar, solo en la suya.
    lugar_id BIGINT NULL,
    -- Para que el club decida quién va primero: el que más paga va arriba.
    orden INT NOT NULL DEFAULT 0,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_sponsor_lugar FOREIGN KEY (lugar_id) REFERENCES lugares(id)
);

-- La franja pública se pide en cada carga de la home y del torneo.
CREATE INDEX idx_sponsor_visible ON sponsors (activo, orden);
