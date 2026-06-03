-- ============================================================
-- RESET DE DATOS — RankPadel
-- Borra todos los datos de torneos, partidos, ranking, etc.
-- Mantiene: admins, categorias, jugadores, lugares, temporadas,
--           plantillas de formato y puntos.
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE ranking_entries;
TRUNCATE TABLE posiciones_grupo;
TRUNCATE TABLE partidos;
TRUNCATE TABLE rondas_eliminatorias;
TRUNCATE TABLE grupos;
TRUNCATE TABLE parejas;
TRUNCATE TABLE configuracion_puntos;
TRUNCATE TABLE torneo_categorias;
TRUNCATE TABLE torneos;

SET FOREIGN_KEY_CHECKS = 1;

-- Si también querés borrar jugadores, lugares, temporadas y plantillas:
-- SET FOREIGN_KEY_CHECKS = 0;
-- TRUNCATE TABLE jugadores;
-- TRUNCATE TABLE lugares;
-- TRUNCATE TABLE temporadas;
-- TRUNCATE TABLE plantillas_puntos_rondas;
-- TRUNCATE TABLE plantillas_puntos;
-- TRUNCATE TABLE plantillas_formato;
-- TRUNCATE TABLE categorias;
-- SET FOREIGN_KEY_CHECKS = 1;
