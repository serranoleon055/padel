-- Cuándo no puede jugar cada pareja. Se guarda lo que NO pueden (la mayoría puede siempre
-- salvo una o dos cosas): un día puntual del torneo o, para una liga, un día de la semana.
-- La solicitud de inscripción guarda lo que cargaron; al aprobarse pasa a la pareja.
--
-- El id no lo mapea nadie (son colecciones de la pareja y la solicitud), pero el MySQL
-- gestionado exige clave primaria en toda tabla (sql_require_primary_key).

CREATE TABLE pareja_restricciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pareja_id BIGINT NOT NULL,
    fecha DATE NULL,
    dia_semana INT NULL,
    hora_desde TIME NOT NULL,
    hora_hasta TIME NOT NULL,
    CONSTRAINT fk_pareja_restriccion FOREIGN KEY (pareja_id) REFERENCES parejas (id) ON DELETE CASCADE
);

CREATE TABLE solicitud_restricciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    solicitud_id BIGINT NOT NULL,
    fecha DATE NULL,
    dia_semana INT NULL,
    hora_desde TIME NOT NULL,
    hora_hasta TIME NOT NULL,
    CONSTRAINT fk_solicitud_restriccion FOREIGN KEY (solicitud_id) REFERENCES solicitudes_inscripcion (id) ON DELETE CASCADE
);

ALTER TABLE parejas ADD COLUMN restricciones_notas VARCHAR(500) NULL;
ALTER TABLE solicitudes_inscripcion ADD COLUMN restricciones_notas VARCHAR(500) NULL;
