ALTER TABLE configuracion_sede DROP COLUMN servicios_json;
ALTER TABLE configuracion_sede DROP COLUMN texto_precios;
ALTER TABLE configuracion_sede DROP COLUMN texto_sede;
ALTER TABLE configuracion_sede DROP COLUMN texto_contacto;
ALTER TABLE configuracion_sede ADD COLUMN aviso_home TEXT NULL;
