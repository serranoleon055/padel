-- "Mercado Pago (sena online)" sin eñe.
--
-- Lo sembró V32 y lo ve el público en /precios hasta que el club edite las formas de pago
-- a mano. Es de las cosas que no rompen nada y quedan años: un cartel mal escrito en la
-- página que el club le pasa a sus clientes.
--
-- V32 quedó corregida también, para que una instalación nueva nazca bien. Esta migración
-- es la que arregla las bases que ya la corrieron: en ellas el archivo viejo ya insertó el
-- texto con el error y `flyway.repair()` solo recalcula el checksum, no vuelve a ejecutar.
UPDATE configuracion_sede
SET formas_pago_json = REPLACE(formas_pago_json, 'sena online', 'seña online')
WHERE formas_pago_json LIKE '%sena online%';
