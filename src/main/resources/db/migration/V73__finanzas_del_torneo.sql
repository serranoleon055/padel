-- Finanzas de cada torneo: premios, gastos y aportes de sponsors. Con las inscripciones
-- (V69) y lo que se le paga a cada club (V68) arman el balance del torneo.

CREATE TABLE premios_torneo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_id BIGINT NOT NULL,
    categoria_id BIGINT NULL,
    puesto VARCHAR(80) NOT NULL,
    descripcion VARCHAR(255) NULL,
    monto DECIMAL(12, 2) NULL,
    entregado BOOLEAN NOT NULL DEFAULT FALSE,
    entregado_en DATETIME NULL,
    entregado_a VARCHAR(255) NULL,
    CONSTRAINT fk_premio_torneo FOREIGN KEY (torneo_id) REFERENCES torneos (id),
    CONSTRAINT fk_premio_categoria FOREIGN KEY (categoria_id) REFERENCES categorias (id)
);

CREATE TABLE gastos_torneo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    descripcion VARCHAR(255) NULL,
    monto DECIMAL(12, 2) NOT NULL,
    medio VARCHAR(20) NOT NULL,
    registrado_por VARCHAR(100) NULL,
    creado_en DATETIME NOT NULL,
    anulado_en DATETIME NULL,
    anulado_por VARCHAR(100) NULL,
    motivo_anulacion VARCHAR(255) NULL,
    CONSTRAINT fk_gasto_torneo_torneo FOREIGN KEY (torneo_id) REFERENCES torneos (id)
);

CREATE TABLE aportes_sponsor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_id BIGINT NOT NULL,
    sponsor_id BIGINT NULL,
    nombre VARCHAR(120) NOT NULL,
    monto DECIMAL(12, 2) NULL,
    descripcion VARCHAR(255) NULL,
    cobrado BOOLEAN NOT NULL DEFAULT FALSE,
    cobrado_en DATE NULL,
    medio VARCHAR(20) NULL,
    CONSTRAINT fk_aporte_torneo FOREIGN KEY (torneo_id) REFERENCES torneos (id),
    CONSTRAINT fk_aporte_sponsor FOREIGN KEY (sponsor_id) REFERENCES sponsors (id)
);
