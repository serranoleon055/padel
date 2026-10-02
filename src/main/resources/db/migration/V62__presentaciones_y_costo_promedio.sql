-- Presentaciones de venta y costo promedio ponderado.
--
-- 1) PRESENTACIONES. El pedido textual: "las pelotas se pueden vender de a 1 o de a 3; en
--    stock tenés tubos y te pueden ir saliendo de a 1". Hasta acá un producto tenía un
--    único precio y una única unidad, así que el club tenía que elegir: o cargaba "tubo
--    de pelotas" y no podía vender una suelta, o cargaba "pelota" y no podía cobrar el
--    tubo a su precio.
--
--    El stock se lleva SIEMPRE en la unidad base (la pelota) y cada presentación dice
--    cuántas unidades base saca (`unidades`) y a qué precio se vende. Vender un tubo
--    descuenta 3. El precio del tubo es un campo propio y no 3 × el de la unidad, que es
--    justo lo que un club quiere poder hacer: el pack sale más barato que la suma.
--
--    Un producto sin presentaciones se comporta exactamente como hasta hoy —su
--    `precio_venta` y factor 1—, así que no hay nada que backfillear y las seis demos no
--    cambian en nada.
--
-- 2) COSTO PROMEDIO PONDERADO. `productos.costo` guarda el costo de la ÚLTIMA compra, y
--    eso es lo que el club tiene en la cabeza, así que se queda. Pero valuar el depósito
--    y medir el margen con el último costo hace que una compra chica a precio raro mueva
--    de golpe el capital en stock y la columna de margen de todo lo que ya había.
--
--    `costo_promedio` se recalcula en cada compra ponderando por las unidades que había y
--    las que entran. La valuación del stock y el margen pasan a usarlo; la ficha muestra
--    los dos. El backfill lo arranca en el costo actual, que es el único dato que existe
--    y además es exactamente el promedio correcto si nunca hubo dos costos distintos.
--
--    Lo que NO se toca es `venta_items.costo_unitario`: el costo congelado de lo ya
--    vendido es el margen con el que se vendió y no se recalcula nunca (V48).

CREATE TABLE presentaciones_producto (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    -- Cuántas unidades base saca del stock. Un tubo de 3 pelotas son 3.
    unidades INT NOT NULL,
    precio_venta DECIMAL(12,2) NOT NULL,
    orden INT NOT NULL DEFAULT 0,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_presentacion_producto FOREIGN KEY (producto_id) REFERENCES productos(id),
    -- Dos presentaciones con el mismo nombre en un producto confunden la venta igual que
    -- dos productos con el mismo nombre (V48).
    CONSTRAINT uq_presentacion_nombre UNIQUE (producto_id, nombre)
);

CREATE INDEX idx_presentacion_producto ON presentaciones_producto (producto_id, activo);

ALTER TABLE venta_items ADD COLUMN presentacion_id BIGINT NULL;
-- Unidades base por cada una vendida, CONGELADO igual que el precio y el costo: cambiar
-- después cuántas pelotas trae un tubo no puede reescribir lo que salió del stock aquel
-- día. Las ventas viejas son todas de unidad suelta.
ALTER TABLE venta_items ADD COLUMN factor INT NOT NULL DEFAULT 1;
ALTER TABLE venta_items
    ADD CONSTRAINT fk_ventaitem_presentacion FOREIGN KEY (presentacion_id)
    REFERENCES presentaciones_producto(id);

ALTER TABLE productos ADD COLUMN costo_promedio DECIMAL(12,2) NULL;
UPDATE productos SET costo_promedio = costo WHERE costo_promedio IS NULL;
