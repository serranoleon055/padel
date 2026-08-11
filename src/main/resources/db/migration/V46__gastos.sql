-- Egresos del club. Sin esto la caja y las estadísticas mostraban ingresos pero no
-- rentabilidad: el dueño veía cuánto entró y nunca cuánto le quedó.
CREATE TABLE gastos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha DATE NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    medio VARCHAR(20) NOT NULL,
    proveedor VARCHAR(120) NULL,
    registrado_por VARCHAR(80) NULL,
    notas VARCHAR(300) NULL,
    creado_en DATETIME NOT NULL
);

-- El cierre de caja filtra por día; las estadísticas agrupan por categoría y mes.
CREATE INDEX idx_gasto_fecha ON gastos (fecha);
CREATE INDEX idx_gasto_categoria ON gastos (categoria, fecha);
