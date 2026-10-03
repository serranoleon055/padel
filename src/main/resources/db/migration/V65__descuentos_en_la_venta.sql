-- Descuentos en la venta de mostrador.
--
-- Hasta acá el precio de la lista era el único precio posible, así que el "te lo dejo en
-- cinco mil" se resolvía cobrando de menos por fuera del sistema: la venta quedaba cargada
-- al precio entero, la caja daba faltante esa noche, y el margen del kiosco salía inflado
-- todos los meses.
--
-- El descuento es un IMPORTE del renglón, no un porcentaje: el mostrador redondea
-- ("quedate con cinco mil"), no calcula. El porcentaje es cómo se escribe, la plata es lo
-- que pasa, y guardar la plata evita que un redondeo distinto en cada pantalla haga que el
-- total no dé.
--
-- Y va por renglón aunque la persona lo piense sobre el total, porque el invariante que
-- importa es `venta.total = suma de los renglones`. Un descuento suelto en la cabecera lo
-- rompe: el ranking de productos suma los renglones y la facturación del mes suma el
-- total, y los dos números dejarían de cerrar. El descuento de la venta entera se prorratea
-- al cargarla.
--
-- El costo congelado NO se toca: el margen baja con el descuento, que es exactamente lo
-- que el dueño tiene que ver.

ALTER TABLE venta_items ADD COLUMN descuento DECIMAL(12,2) NOT NULL DEFAULT 0;

-- La suma de los descuentos de los renglones, para poder preguntar "cuánto se bonificó
-- este mes" sin recorrer los renglones. Es derivado y se escribe junto con ellos.
ALTER TABLE ventas ADD COLUMN descuento DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE ventas ADD COLUMN motivo_descuento VARCHAR(200) NULL;

-- Hasta qué porcentaje puede bonificar el mostrador sin que lo autorice el dueño. En 0 no
-- puede descontar nada; null se trata como 0. El dueño nunca tiene tope.
ALTER TABLE configuracion_sede ADD COLUMN descuento_maximo_mostrador INT NOT NULL DEFAULT 0;
