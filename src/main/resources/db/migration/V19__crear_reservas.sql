CREATE TABLE horarios_cancha (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cancha_id BIGINT NOT NULL,
    hora_apertura TIME NOT NULL,
    hora_cierre TIME NOT NULL,
    dias_activos VARCHAR(40) NOT NULL,
    duracion_slot_min INT NOT NULL DEFAULT 60,
    anticipacion_dias INT NOT NULL DEFAULT 14,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT fk_horario_cancha FOREIGN KEY (cancha_id) REFERENCES canchas(id)
);

CREATE TABLE reservas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cancha_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    cliente_nombre VARCHAR(120) NOT NULL,
    cliente_telefono VARCHAR(40) NOT NULL,
    codigo VARCHAR(12) NOT NULL,
    creado_en DATETIME NOT NULL,
    confirmado_en DATETIME NULL,
    expira_en DATETIME NULL,
    clave_slot VARCHAR(80) NULL,
    CONSTRAINT fk_reserva_cancha FOREIGN KEY (cancha_id) REFERENCES canchas(id),
    CONSTRAINT uq_reserva_slot_activo UNIQUE (clave_slot)
);

CREATE TABLE bloqueos_cancha (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cancha_id BIGINT NOT NULL,
    inicio DATETIME NOT NULL,
    fin DATETIME NOT NULL,
    motivo VARCHAR(200) NULL,
    CONSTRAINT fk_bloqueo_cancha FOREIGN KEY (cancha_id) REFERENCES canchas(id)
);
