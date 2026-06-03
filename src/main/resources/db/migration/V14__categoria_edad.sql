-- Agrega rangos de edad opcionales a las categorías (veteranos, junior, sub-23, etc.)
ALTER TABLE categorias
    ADD COLUMN edad_min INT DEFAULT NULL AFTER nivel,
    ADD COLUMN edad_max INT DEFAULT NULL AFTER edad_min;
