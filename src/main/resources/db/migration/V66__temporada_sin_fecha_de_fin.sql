-- Una temporada en curso todavia no tiene fecha de fin: el panel la deja vacia y la
-- muestra como "En curso". La columna la exigia y el alta respondia 500.
ALTER TABLE temporadas MODIFY fecha_fin DATE NULL;
