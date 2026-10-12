-- En un club, el cobro de una inscripción entra a la caja del día como un movimiento de
-- concepto INSCRIPCION_TORNEO. El pago guarda cuál, para anularlos juntos.
ALTER TABLE pagos_inscripcion ADD COLUMN movimiento_caja_id BIGINT NULL;
ALTER TABLE pagos_inscripcion
    ADD CONSTRAINT fk_pago_insc_movimiento FOREIGN KEY (movimiento_caja_id) REFERENCES movimientos_caja (id);
