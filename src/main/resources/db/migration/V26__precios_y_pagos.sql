CREATE TABLE pagos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    concepto VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    monto_total DECIMAL(10,2) NOT NULL,
    monto_senia DECIMAL(10,2) NOT NULL,
    porcentaje_senia INT NOT NULL,
    referencia_externa VARCHAR(40) NOT NULL,
    preferencia_id VARCHAR(120) NULL,
    pago_mercado_pago_id VARCHAR(60) NULL,
    cliente_nombre VARCHAR(120) NULL,
    cliente_telefono VARCHAR(40) NULL,
    creado_en DATETIME NOT NULL,
    pagado_en DATETIME NULL,
    CONSTRAINT uq_pago_referencia_externa UNIQUE (referencia_externa)
);

ALTER TABLE canchas
    ADD COLUMN precio_por_hora DECIMAL(10,2) NULL,
    ADD COLUMN senia_porcentaje INT NULL;

ALTER TABLE torneos
    ADD COLUMN costo_inscripcion_jugador DECIMAL(10,2) NULL,
    ADD COLUMN premio_acumulado DECIMAL(10,2) NULL,
    ADD COLUMN senia_porcentaje INT NULL;

ALTER TABLE reservas
    ADD COLUMN pago_id BIGINT NULL,
    ADD CONSTRAINT fk_reserva_pago FOREIGN KEY (pago_id) REFERENCES pagos(id);

ALTER TABLE solicitudes_inscripcion
    ADD COLUMN pagada TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN pago_id BIGINT NULL,
    ADD CONSTRAINT fk_solicitud_pago FOREIGN KEY (pago_id) REFERENCES pagos(id);
