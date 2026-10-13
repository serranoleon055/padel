-- Un torneo se juega en uno o más clubes. El organizador le alquila canchas a terceros:
-- cada club cede algunas canchas en ciertos días y horarios, y se le paga un monto
-- acordado por todo el torneo, en uno o varios pagos.
--
-- torneo_sede_franjas lleva un id que nadie mapea: el MySQL gestionado exige clave
-- primaria en toda tabla (sql_require_primary_key).
--
-- torneos.lugar_id se conserva como el club principal (lo que se muestra en la tarjeta
-- del torneo y lo que usa un club para su sede). Cada torneo existente pasa a tener su
-- lugar como única sede, con todas sus canchas activas.

CREATE TABLE torneo_sedes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_id BIGINT NOT NULL,
    lugar_id BIGINT NOT NULL,
    monto_acordado DECIMAL(12, 2) NULL,
    notas VARCHAR(500) NULL,
    CONSTRAINT fk_torneo_sede_torneo FOREIGN KEY (torneo_id) REFERENCES torneos (id),
    CONSTRAINT fk_torneo_sede_lugar FOREIGN KEY (lugar_id) REFERENCES lugares (id),
    CONSTRAINT uk_torneo_sede UNIQUE (torneo_id, lugar_id)
);

CREATE TABLE torneo_sede_canchas (
    torneo_sede_id BIGINT NOT NULL,
    cancha_id BIGINT NOT NULL,
    PRIMARY KEY (torneo_sede_id, cancha_id),
    CONSTRAINT fk_tsc_sede FOREIGN KEY (torneo_sede_id) REFERENCES torneo_sedes (id) ON DELETE CASCADE,
    CONSTRAINT fk_tsc_cancha FOREIGN KEY (cancha_id) REFERENCES canchas (id)
);

CREATE TABLE torneo_sede_franjas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_sede_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    hora_desde TIME NOT NULL,
    hora_hasta TIME NOT NULL,
    CONSTRAINT fk_tsf_sede FOREIGN KEY (torneo_sede_id) REFERENCES torneo_sedes (id) ON DELETE CASCADE
);

CREATE TABLE pagos_sede (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_sede_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    monto DECIMAL(12, 2) NOT NULL,
    medio VARCHAR(20) NOT NULL,
    notas VARCHAR(255) NULL,
    registrado_por VARCHAR(100) NULL,
    creado_en DATETIME NOT NULL,
    anulado_en DATETIME NULL,
    anulado_por VARCHAR(100) NULL,
    motivo_anulacion VARCHAR(255) NULL,
    CONSTRAINT fk_pago_sede_sede FOREIGN KEY (torneo_sede_id) REFERENCES torneo_sedes (id)
);

INSERT INTO torneo_sedes (torneo_id, lugar_id)
SELECT id, lugar_id FROM torneos WHERE lugar_id IS NOT NULL;

INSERT INTO torneo_sede_canchas (torneo_sede_id, cancha_id)
SELECT ts.id, c.id
FROM torneo_sedes ts
JOIN canchas c ON c.lugar_id = ts.lugar_id AND c.activo = TRUE;
