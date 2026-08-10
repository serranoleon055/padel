-- Precio por franja horaria. Hasta ahora la tarifa era plana (canchas.precio_por_hora),
-- pero un club cobra distinto un martes a las 15 que un viernes a las 22: es su principal
-- palanca de ingresos.
--
-- canchas.precio_por_hora se conserva como tarifa por defecto: un club que no arma franjas
-- sigue funcionando igual que antes.
CREATE TABLE tarifas_cancha (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cancha_id BIGINT NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    dias_semana VARCHAR(20) NOT NULL,          -- ISO: 1=lunes ... 7=domingo, separados por coma
    hora_desde TIME NOT NULL,
    hora_hasta TIME NOT NULL,                  -- si es <= hora_desde, la franja cruza medianoche
    precio_por_hora DECIMAL(12,2) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT fk_tarifa_cancha FOREIGN KEY (cancha_id) REFERENCES canchas(id)
);

CREATE INDEX idx_tarifa_cancha ON tarifas_cancha (cancha_id, activo);
