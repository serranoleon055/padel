-- Índices para los filtros más frecuentes que hoy escanean tabla completa:
-- tope anti-spam por teléfono (cada reserva), scheduler de expiración (cada 3 min),
-- scheduler de pagos pendientes (cada 20 s) y consultas por fecha (disponibilidad/estadísticas).

CREATE INDEX idx_reserva_tel_estado ON reservas (cliente_telefono, estado);
CREATE INDEX idx_reserva_estado_expira ON reservas (estado, expira_en);
CREATE INDEX idx_reserva_fecha ON reservas (fecha);
CREATE INDEX idx_pago_estado_creado ON pagos (estado, creado_en);
CREATE INDEX idx_solicitud_estado ON solicitudes_inscripcion (estado);
