-- Recuento de inventario físico.
--
-- Hasta acá la única forma de corregir el stock era el ajuste de a un producto, con el
-- club parado delante de la heladera tocando de a uno y sin nada que registre que ese día
-- se contó todo. El recuento es la planilla: se congela lo que dice el sistema, se anota
-- lo contado, y recién al aplicarlo salen los movimientos.
--
-- Dos cosas que decide el diseño y conviene no desandar:
--
-- `stock_sistema` se congela cuando el renglón entra a la planilla, no cuando se aplica.
-- Entre que el club empieza a contar y termina siguen entrando ventas, y aplicar el conteo
-- como un valor absoluto ("el stock pasa a ser 12") borraría esas ventas del inventario.
-- Lo que el conteo dice es una DIFERENCIA medida en un momento —faltan tres— y eso es lo
-- que se aplica: el movimiento es `contado - stock_sistema`.
--
-- El costo también se congela por renglón: el faltante valorizado es lo que valía la
-- mercadería el día que se contó, y una compra posterior a otro precio no puede cambiar
-- cuánto se perdió ese día. Mismo criterio que `venta_items.costo_unitario` (V48) y que
-- `reservas.precio_aplicado` (V41).

CREATE TABLE recuentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha DATE NOT NULL,
    estado VARCHAR(20) NOT NULL,
    notas VARCHAR(300) NULL,
    creado_en DATETIME NOT NULL,
    creado_por VARCHAR(80) NULL,
    aplicado_en DATETIME NULL,
    aplicado_por VARCHAR(80) NULL,
    -- Congelados al aplicar, como los totales del arqueo: corregir el costo de un producto
    -- después no puede mover lo que un recuento ya firmado dijo que faltaba.
    faltante_valorizado DECIMAL(12,2) NULL,
    sobrante_valorizado DECIMAL(12,2) NULL
);

CREATE INDEX idx_recuentos_fecha ON recuentos (fecha, estado);

CREATE TABLE recuento_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recuento_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    stock_sistema INT NOT NULL,
    -- Null = todavía no se contó. Es distinto de cero, que significa "no quedaba ninguno",
    -- y por eso no tiene default: un renglón sin contar no genera ajuste.
    stock_contado INT NULL,
    costo_unitario DECIMAL(12,2) NULL,
    CONSTRAINT fk_recuento_item_recuento FOREIGN KEY (recuento_id) REFERENCES recuentos (id),
    CONSTRAINT fk_recuento_item_producto FOREIGN KEY (producto_id) REFERENCES productos (id),
    CONSTRAINT uk_recuento_producto UNIQUE (recuento_id, producto_id)
);

-- De qué recuento salió el ajuste. Mismo criterio que `documento_compra_id` (V63): el
-- movimiento tiene que poder explicarse, y "AJUSTE de -3" sin decir de dónde salió es
-- exactamente el faltante sin explicación que el kardex existe para evitar.
ALTER TABLE movimientos_stock ADD COLUMN recuento_id BIGINT NULL;
ALTER TABLE movimientos_stock ADD CONSTRAINT fk_movstock_recuento
    FOREIGN KEY (recuento_id) REFERENCES recuentos (id);
CREATE INDEX idx_movstock_recuento ON movimientos_stock (recuento_id);
