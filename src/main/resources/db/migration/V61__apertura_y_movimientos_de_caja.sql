-- Apertura de caja y movimientos sueltos.
--
-- Dos huecos que hacían que el arqueo no cerrara nunca, por más bien que estuvieran
-- sumados los cobros.
--
-- 1) FONDO INICIAL. El efectivo esperado arrancaba siempre de cero. Todo club deja cambio
--    en el cajón para poder dar vuelto, así que la diferencia del arqueo salía positiva
--    TODAS las noches por el monto del fondo, y el número que el dueño mira para saber si
--    le falta plata no servía para nada.
--
-- 2) PLATA QUE NO ES UN TURNO NI UN KIOSCO. `cobros.reserva_id` es NOT NULL, así que todo
--    ingreso tenía que colgar de una reserva o ser una venta de producto. Un depósito al
--    banco, un retiro del dueño, el alquiler del salón, una clase particular o la
--    devolución de un préstamo no tenían dónde anotarse. Lo único que quedaba era meterlos
--    como gasto, y eso le ensucia la rentabilidad: un retiro del dueño no es un gasto del
--    club.
--
-- Por qué una tabla nueva y no relajar `cobros.reserva_id`: ese NOT NULL es lo que hace
-- que `MontosReserva` sea la única fuente de verdad del saldo de un turno, y lo que
-- permite que `CobroService` valide "no cobrar de más" y "turno cobrable". Hacerlo
-- nullable mete un `if (reserva != null)` en cada uno de esos invariantes y el guard de no
-- cobrar de más dejaría de aplicar, en silencio, a la mitad de las filas. Un depósito al
-- banco no es el pago parcial de nada.
--
-- Y la diferencia con `gastos`, que hay que respetar: un GASTO pesa en el estado de
-- resultados (luz, insumos, sueldos). Un MOVIMIENTO DE CAJA de tipo EGRESO es plata que
-- sale del cajón sin ser un gasto del negocio (un depósito al banco cambia de lugar, no
-- desaparece; un retiro del dueño es distribución de utilidad). Los dos bajan el efectivo
-- esperado; solo el gasto baja el resultado.
--
-- La apertura es un movimiento más, de concepto APERTURA: el fondo con el que arranca el
-- día es plata que entra al cajón. Así no hace falta una entidad de sesión aparte y el
-- "¿está abierta la caja?" es una consulta sobre esta misma tabla.

CREATE TABLE movimientos_caja (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- Jornada del club, igual que cobros y ventas: se estampa al insertar y no se
    -- recalcula nunca.
    jornada DATE NOT NULL,
    fecha DATETIME NOT NULL,
    -- INGRESO | EGRESO. El monto va siempre en positivo y el signo lo da el tipo, así
    -- nadie puede tipear un egreso en negativo y convertirlo en un ingreso.
    tipo VARCHAR(10) NOT NULL,
    -- APERTURA | DEPOSITO_BANCO | RETIRO_DUENIO | PRESTAMO | COBRO_VARIO | AJUSTE | OTRO
    concepto VARCHAR(30) NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    medio VARCHAR(20) NOT NULL,
    cliente_id BIGINT NULL,
    registrado_por VARCHAR(80) NULL,
    notas VARCHAR(300) NULL,
    anulado_en DATETIME NULL,
    anulado_por VARCHAR(120) NULL,
    motivo_anulacion VARCHAR(300) NULL,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_movcaja_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

-- El par que usan todas las consultas del arqueo.
CREATE INDEX idx_movcaja_jornada ON movimientos_caja (jornada, anulado_en);

-- Los totales del arqueo se congelan al firmar, así que los tres conceptos nuevos tienen
-- que guardarse también. El DEFAULT 0 en los arqueos viejos es exactamente correcto: se
-- calcularon sin fondo y sin movimientos sueltos, porque no existían.
ALTER TABLE cierres_caja ADD COLUMN fondo_inicial DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cierres_caja ADD COLUMN movimientos_ingreso DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cierres_caja ADD COLUMN movimientos_egreso DECIMAL(12,2) NOT NULL DEFAULT 0;

-- El fondo que el club deja en el cajón todos los días. Es solo la propuesta del
-- formulario de apertura: lo que vale es el monto que se declara al abrir.
ALTER TABLE configuracion_sede ADD COLUMN fondo_fijo DECIMAL(12,2) NULL;
