-- Posicion del partido dentro de la ronda eliminatoria para preservar el lado del cuadro.
ALTER TABLE partidos
    ADD COLUMN orden_llave INT DEFAULT NULL AFTER fecha_hora_programada;
