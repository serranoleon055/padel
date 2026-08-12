-- Punto de venta del club: pelotas, paletas, bebidas, alquiler de paletas, bar.
--
-- Hasta ahora el sistema solo sabía de canchas. Todo lo que se vende en el mostrador
-- quedaba fuera de la caja y de la rentabilidad, que es justo donde el club tiene el
-- margen más alto. Con esto la venta entra al cierre del día por el mismo camino que el
-- cobro de un turno.

CREATE TABLE proveedores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    telefono VARCHAR(40) NULL,
    notas VARCHAR(300) NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en DATETIME NOT NULL
);

CREATE TABLE productos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    precio_venta DECIMAL(12,2) NOT NULL,
    -- Cuánto le cuesta al club. Sin esto no hay margen, que es el número que decide qué
    -- conviene tener en la vitrina.
    costo DECIMAL(12,2) NULL,
    -- Un alquiler de paleta o un café no tienen unidades que se acaben: para esos el
    -- control de stock se apaga y el sistema deja vender sin descontar nada.
    controla_stock TINYINT(1) NOT NULL DEFAULT 1,
    stock INT NOT NULL DEFAULT 0,
    stock_minimo INT NOT NULL DEFAULT 0,
    proveedor_id BIGINT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_producto_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id)
);

CREATE INDEX idx_producto_activo ON productos (activo, categoria, nombre);

CREATE TABLE ventas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha DATETIME NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    medio VARCHAR(20) NOT NULL,
    -- Opcionales: sirven para saber quién compró y para cobrar la consumición junto con
    -- el turno, pero una venta de mostrador no necesita ninguno de los dos.
    cliente_id BIGINT NULL,
    reserva_id BIGINT NULL,
    registrado_por VARCHAR(80) NULL,
    notas VARCHAR(300) NULL,
    CONSTRAINT fk_venta_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    CONSTRAINT fk_venta_reserva FOREIGN KEY (reserva_id) REFERENCES reservas(id)
);

CREATE INDEX idx_venta_fecha ON ventas (fecha);

CREATE TABLE venta_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    venta_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    cantidad INT NOT NULL,
    -- Precio y costo se congelan en la venta: el histórico no puede cambiar porque
    -- después se haya actualizado la lista de precios.
    precio_unitario DECIMAL(12,2) NOT NULL,
    costo_unitario DECIMAL(12,2) NULL,
    CONSTRAINT fk_item_venta FOREIGN KEY (venta_id) REFERENCES ventas(id),
    CONSTRAINT fk_item_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
);

CREATE INDEX idx_item_venta ON venta_items (venta_id);
CREATE INDEX idx_item_producto ON venta_items (producto_id);

-- Todo movimiento de stock queda registrado: sin esto, un faltante no se puede explicar.
CREATE TABLE movimientos_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT NOT NULL,
    -- Negativo al vender, positivo al comprar. El stock del producto es la suma.
    cantidad INT NOT NULL,
    motivo VARCHAR(20) NOT NULL,
    fecha DATETIME NOT NULL,
    venta_id BIGINT NULL,
    costo_unitario DECIMAL(12,2) NULL,
    registrado_por VARCHAR(80) NULL,
    notas VARCHAR(300) NULL,
    CONSTRAINT fk_movimiento_producto FOREIGN KEY (producto_id) REFERENCES productos(id),
    CONSTRAINT fk_movimiento_venta FOREIGN KEY (venta_id) REFERENCES ventas(id)
);

CREATE INDEX idx_movimiento_producto ON movimientos_stock (producto_id, fecha);

-- La compra de mercadería es un gasto como cualquier otro: se enlaza para poder ir del
-- movimiento de stock al egreso que lo pagó.
ALTER TABLE gastos ADD COLUMN producto_id BIGINT NULL;
ALTER TABLE gastos ADD CONSTRAINT fk_gasto_producto FOREIGN KEY (producto_id) REFERENCES productos(id);
