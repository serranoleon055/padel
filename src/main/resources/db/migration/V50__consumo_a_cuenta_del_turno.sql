-- Cuenta abierta del turno: lo que el grupo consume mientras juega se anota en el turno
-- y se cobra todo junto al final, como la cuenta de un bar. Antes había que cobrar la
-- cancha por un lado y cada bebida por otro, y el mostrador terminaba haciendo dos
-- movimientos por el mismo cliente.
--
-- El medio de pago pasa a ser opcional: sin medio, la venta NO es plata que entró
-- todavía —es deuda del turno— y por eso no puede entrar al arqueo de caja hasta que se
-- registre el cobro. Una venta sin medio y sin turno sería plata que se pierde de vista,
-- así que se prohíbe.
ALTER TABLE ventas MODIFY COLUMN medio VARCHAR(20) NULL;

ALTER TABLE ventas ADD CONSTRAINT ck_venta_sin_medio_va_a_un_turno
    CHECK (medio IS NOT NULL OR reserva_id IS NOT NULL);

-- El cierre de caja y la ficha del cliente piden el consumo de un turno a cada rato.
CREATE INDEX idx_venta_reserva ON ventas (reserva_id);
