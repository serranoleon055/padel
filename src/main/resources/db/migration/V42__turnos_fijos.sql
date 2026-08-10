-- Turnos fijos (abonos): el mismo cliente, la misma cancha, el mismo día y hora, todas
-- las semanas. Es el grueso de la facturación de un club y hasta ahora había que cargar
-- la misma reserva una por una.
CREATE TABLE turnos_fijos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cancha_id BIGINT NOT NULL,
    dia_semana INT NOT NULL,                     -- ISO: 1=lunes ... 7=domingo
    hora_inicio TIME NOT NULL,
    slots INT NOT NULL DEFAULT 1,                -- 2 = turno de dos horas
    cliente_nombre VARCHAR(120) NOT NULL,
    cliente_telefono VARCHAR(40) NOT NULL,
    precio_pactado DECIMAL(12,2) NULL,           -- NULL = usa la tarifa de la cancha
    vigente_desde DATE NOT NULL,
    vigente_hasta DATE NULL,                     -- NULL = sin fecha de corte
    activo TINYINT(1) NOT NULL DEFAULT 1,
    notas VARCHAR(300) NULL,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_turno_fijo_cancha FOREIGN KEY (cancha_id) REFERENCES canchas(id)
);

CREATE INDEX idx_turno_fijo_generacion ON turnos_fijos (activo, dia_semana);
CREATE INDEX idx_turno_fijo_cancha ON turnos_fijos (cancha_id);

-- Las reservas generadas quedan atadas a su turno fijo: sirve para no regenerar una que
-- el club dio de baja (la ausencia de una semana puntual) y para distinguirlas en el panel.
ALTER TABLE reservas ADD COLUMN turno_fijo_id BIGINT NULL;
ALTER TABLE reservas ADD CONSTRAINT fk_reserva_turno_fijo
    FOREIGN KEY (turno_fijo_id) REFERENCES turnos_fijos(id);
CREATE INDEX idx_reserva_turno_fijo ON reservas (turno_fijo_id, fecha);
