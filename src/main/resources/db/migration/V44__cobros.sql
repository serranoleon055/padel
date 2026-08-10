-- Cobros hechos en el club. La seña online cubre el 50% del turno; el resto se cobraba
-- en el mostrador y no quedaba registrado en ningún lado: el club no sabía quién debía
-- ni cuánta plata había entrado en el día.
CREATE TABLE cobros (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reserva_id BIGINT NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    medio VARCHAR(20) NOT NULL,
    cobrado_en DATETIME NOT NULL,
    registrado_por VARCHAR(80) NULL,
    notas VARCHAR(300) NULL,
    CONSTRAINT fk_cobro_reserva FOREIGN KEY (reserva_id) REFERENCES reservas(id)
);

-- El cierre de caja filtra por día; el detalle del turno, por reserva.
CREATE INDEX idx_cobro_fecha ON cobros (cobrado_en);
CREATE INDEX idx_cobro_reserva ON cobros (reserva_id);
