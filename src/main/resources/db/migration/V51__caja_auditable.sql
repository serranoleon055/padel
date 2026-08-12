-- La caja pasa a ser auditable.
--
-- Hasta acá, anular un cobro o una venta borraba la fila. Eso tenía dos consecuencias
-- que no se pueden sostener en un módulo que maneja plata: el cierre de un día pasado
-- cambiaba solo si alguien anulaba algo de esa fecha, y no había forma de contestar
-- "¿quién anuló los $40.000 del sábado?" sin entrar a los logs del servidor.
--
-- Ahora la anulación es una baja lógica con autor y motivo: la fila queda, sale de todos
-- los totales y se puede mirar aparte.

ALTER TABLE cobros
    ADD COLUMN anulado_en DATETIME NULL,
    ADD COLUMN anulado_por VARCHAR(120) NULL,
    ADD COLUMN motivo_anulacion VARCHAR(300) NULL;

ALTER TABLE ventas
    ADD COLUMN anulado_en DATETIME NULL,
    ADD COLUMN anulado_por VARCHAR(120) NULL,
    ADD COLUMN motivo_anulacion VARCHAR(300) NULL;

-- Todas las consultas de caja, saldo y estadísticas filtran por "no anulado", así que la
-- columna entra en el índice junto a la fecha por la que ya se buscaba.
CREATE INDEX idx_cobros_anulado ON cobros (anulado_en, cobrado_en);
CREATE INDEX idx_ventas_anulado ON ventas (anulado_en, fecha);

-- Cierre de caja firmado.
--
-- Antes "cierre de caja" era una pantalla que calculaba, no un acto que quedaba
-- registrado. Faltaba el momento en que alguien cuenta el cajón y dice cuánto había, que
-- es lo único que produce el número que al dueño le importa: la diferencia entre lo que
-- el sistema esperaba y lo que apareció.
--
-- Los totales se guardan congelados a propósito. Si mañana se corrige un turno viejo, el
-- arqueo firmado de ayer no puede cambiar: era lo que se sabía en ese momento.
CREATE TABLE cierres_caja (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha DATE NOT NULL,
    efectivo_esperado DECIMAL(12,2) NOT NULL,
    efectivo_contado DECIMAL(12,2) NOT NULL,
    -- Contado menos esperado. Negativo = falta plata en el cajón.
    diferencia DECIMAL(12,2) NOT NULL,
    total_mostrador DECIMAL(12,2) NOT NULL,
    senias_online DECIMAL(12,2) NOT NULL,
    egresos DECIMAL(12,2) NOT NULL,
    cerrado_por VARCHAR(120) NULL,
    cerrado_en DATETIME NOT NULL,
    notas VARCHAR(300) NULL,
    -- Un día se cierra una sola vez: el segundo cierre sería otro arqueo del mismo dinero.
    CONSTRAINT uk_cierres_caja_fecha UNIQUE (fecha)
);
