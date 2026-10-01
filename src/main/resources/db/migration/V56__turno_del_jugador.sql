-- El jugador puede ver y cancelar su turno sin llamar al club.
--
-- Hasta acá, después de reservar la persona se quedaba con un código en pantalla y nada
-- más: si cerraba la pestaña no le quedaba comprobante, y para cancelar tenía que llamar.
-- Del otro lado, el club se enteraba tarde de una cancha que se liberaba y ya no podía
-- vender esa hora.
--
-- La identidad NO cambia: el cliente sigue siendo su teléfono (V43) y no hay usuarios ni
-- contraseñas. Lo que autoriza es un enlace con un token, como el localizador de un pasaje:
-- da acceso a ESE turno y a ninguna otra cosa.

-- Token del enlace. Va aparte de `codigo` a propósito: el código es corto, se lee por
-- teléfono y se muestra en las pantallas del mostrador, así que no puede ser el secreto.
-- Mismo criterio que `pagos.referencia_externa`, que nunca es un id secuencial.
ALTER TABLE reservas ADD COLUMN token_publico VARCHAR(36) NULL;
UPDATE reservas SET token_publico = UUID() WHERE token_publico IS NULL;
ALTER TABLE reservas MODIFY COLUMN token_publico VARCHAR(36) NOT NULL;
CREATE UNIQUE INDEX uk_reservas_token_publico ON reservas (token_publico);

-- El mail que dejó la persona AL RESERVAR, que puede no ser el de su ficha: mismo criterio
-- que `cliente_nombre` y `cliente_telefono`, que son lo que escribió ese día y no se pisan
-- si después se corrige la ficha. Es opcional: sin mail se reserva igual.
ALTER TABLE reservas ADD COLUMN cliente_email VARCHAR(160) NULL;

-- Cuándo se le mandó el recordatorio, para no mandarlo dos veces. Null = todavía no.
ALTER TABLE reservas ADD COLUMN recordatorio_enviado_en DATETIME NULL;

-- Hasta cuántas horas antes del turno el jugador puede cancelar solo. Es política del
-- club: uno que trabaja con seña alta va a querer una ventana más larga que uno que no
-- cobra nada por adelantado. En 0 se apaga la cancelación por internet.
ALTER TABLE configuracion_sede ADD COLUMN cancelacion_horas_minimas INT NOT NULL DEFAULT 12;
