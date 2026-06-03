UPDATE torneos t
SET t.temporada_id = NULL
WHERE t.temporada_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM temporadas s WHERE s.id = t.temporada_id);

UPDATE torneos t
SET t.lugar_id = NULL
WHERE t.lugar_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM lugares l WHERE l.id = t.lugar_id);

UPDATE jugadores j
SET j.categoria_id = NULL
WHERE j.categoria_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM categorias c WHERE c.id = j.categoria_id);

DELETE FROM posiciones_grupo
WHERE pareja_id IN (
    SELECT p.id FROM parejas p
    WHERE NOT EXISTS (SELECT 1 FROM categorias c WHERE c.id = p.categoria_id)
);

DELETE FROM partidos
WHERE pareja_local_id IN (
    SELECT p.id FROM parejas p
    WHERE NOT EXISTS (SELECT 1 FROM categorias c WHERE c.id = p.categoria_id)
)
OR pareja_visitante_id IN (
    SELECT p.id FROM parejas p
    WHERE NOT EXISTS (SELECT 1 FROM categorias c WHERE c.id = p.categoria_id)
)
OR ganador_id IN (
    SELECT p.id FROM parejas p
    WHERE NOT EXISTS (SELECT 1 FROM categorias c WHERE c.id = p.categoria_id)
);

DELETE FROM parejas
WHERE NOT EXISTS (SELECT 1 FROM categorias c WHERE c.id = categoria_id);

DELETE FROM ranking_entries
WHERE NOT EXISTS (SELECT 1 FROM categorias c WHERE c.id = categoria_id);


UPDATE ranking_entries r
SET r.temporada_id = NULL
WHERE r.temporada_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM temporadas s WHERE s.id = r.temporada_id);


DELETE FROM torneo_categorias
WHERE NOT EXISTS (SELECT 1 FROM categorias c WHERE c.id = categoria_id);