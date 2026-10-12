-- Pagos de inscripción por jugador, con pagos parciales.
--
-- Hasta acá la inscripción era "pagada sí/no" por solicitud, y solo para las que entraban
-- por la web. Un organizador cobra en la cancha, por transferencia, de a partes, y cada
-- jugador de la pareja por su lado: necesita saber quién debe y cuánto.

-- Lo que debe cada jugador queda congelado en la pareja al anotarla (V60 ya lo hacía con
-- la solicitud). Las parejas existentes toman el de su solicitud aprobada si la tienen; si
-- no, el precio actual del torneo.
ALTER TABLE parejas ADD COLUMN costo_inscripcion_jugador DECIMAL(12, 2) NULL;

UPDATE parejas p
    JOIN torneos t ON t.id = p.torneo_id
SET p.costo_inscripcion_jugador = t.costo_inscripcion_jugador;

UPDATE parejas p
    JOIN solicitudes_inscripcion s
        ON s.torneo_id = p.torneo_id
        AND s.categoria_id = p.categoria_id
        AND s.jugador1_id = p.jugador1_id
        AND s.jugador2_id = p.jugador2_id
        AND s.estado = 'APROBADA'
SET p.costo_inscripcion_jugador = s.costo_aplicado
WHERE s.costo_aplicado IS NOT NULL;

CREATE TABLE pagos_inscripcion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pareja_id BIGINT NOT NULL,
    jugador_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    monto DECIMAL(12, 2) NOT NULL,
    medio VARCHAR(20) NOT NULL,
    notas VARCHAR(255) NULL,
    pago_id BIGINT NULL,
    registrado_por VARCHAR(100) NULL,
    creado_en DATETIME NOT NULL,
    anulado_en DATETIME NULL,
    anulado_por VARCHAR(100) NULL,
    motivo_anulacion VARCHAR(255) NULL,
    CONSTRAINT fk_pago_insc_pareja FOREIGN KEY (pareja_id) REFERENCES parejas (id),
    CONSTRAINT fk_pago_insc_jugador FOREIGN KEY (jugador_id) REFERENCES jugadores (id),
    CONSTRAINT fk_pago_insc_pago FOREIGN KEY (pago_id) REFERENCES pagos (id)
);

CREATE INDEX idx_pago_insc_pareja ON pagos_inscripcion (pareja_id);

-- Las señas ya cobradas por Mercado Pago pasan a ser pagos de los dos jugadores, mitad
-- cada uno (la seña era por la pareja). Solo las solicitudes aprobadas cuyos dos jugadores
-- ya existían: las que crearon jugadores nuevos no guardaron a quién, y esas quedan para
-- cargar a mano.
INSERT INTO pagos_inscripcion (pareja_id, jugador_id, fecha, monto, medio, notas, pago_id, registrado_por, creado_en)
SELECT p.id, p.jugador1_id, DATE(COALESCE(pg.pagado_en, pg.creado_en)), ROUND(pg.monto_senia / 2, 2),
       'MERCADO_PAGO', 'Seña online', pg.id, 'sistema', NOW()
FROM solicitudes_inscripcion s
    JOIN pagos pg ON pg.id = s.pago_id
    JOIN parejas p
        ON p.torneo_id = s.torneo_id AND p.categoria_id = s.categoria_id
        AND p.jugador1_id = s.jugador1_id AND p.jugador2_id = s.jugador2_id
WHERE s.estado = 'APROBADA' AND s.pagada = TRUE AND pg.monto_senia IS NOT NULL;

INSERT INTO pagos_inscripcion (pareja_id, jugador_id, fecha, monto, medio, notas, pago_id, registrado_por, creado_en)
SELECT p.id, p.jugador2_id, DATE(COALESCE(pg.pagado_en, pg.creado_en)), pg.monto_senia - ROUND(pg.monto_senia / 2, 2),
       'MERCADO_PAGO', 'Seña online', pg.id, 'sistema', NOW()
FROM solicitudes_inscripcion s
    JOIN pagos pg ON pg.id = s.pago_id
    JOIN parejas p
        ON p.torneo_id = s.torneo_id AND p.categoria_id = s.categoria_id
        AND p.jugador1_id = s.jugador1_id AND p.jugador2_id = s.jugador2_id
WHERE s.estado = 'APROBADA' AND s.pagada = TRUE AND pg.monto_senia IS NOT NULL;

-- La solicitud aprobada recuerda qué pareja generó: así, si la seña online se acredita
-- después de aprobarla, el pago llega a la pareja correcta aunque sus jugadores se hayan
-- creado en la aprobación.
ALTER TABLE solicitudes_inscripcion ADD COLUMN pareja_id BIGINT NULL;
ALTER TABLE solicitudes_inscripcion
    ADD CONSTRAINT fk_solicitud_pareja FOREIGN KEY (pareja_id) REFERENCES parejas (id) ON DELETE SET NULL;

UPDATE solicitudes_inscripcion s
    JOIN parejas p
        ON p.torneo_id = s.torneo_id AND p.categoria_id = s.categoria_id
        AND p.jugador1_id = s.jugador1_id AND p.jugador2_id = s.jugador2_id
SET s.pareja_id = p.id
WHERE s.estado = 'APROBADA';
