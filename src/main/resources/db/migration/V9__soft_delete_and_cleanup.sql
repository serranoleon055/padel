ALTER TABLE jugadores
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE AFTER fecha_registro;

ALTER TABLE torneos
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE AFTER suma_puntos_ranking;

DELETE FROM ranking_entries
WHERE jugador_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = jugador_id);

DELETE FROM posiciones_grupo
WHERE pareja_id IN (
    SELECT p.id FROM parejas p
    WHERE (p.jugador1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = p.jugador1_id))
       OR (p.jugador2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = p.jugador2_id))
);

DELETE FROM partidos
WHERE pareja_local_id IN (
    SELECT p.id FROM parejas p
    WHERE (p.jugador1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = p.jugador1_id))
       OR (p.jugador2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = p.jugador2_id))
)
OR pareja_visitante_id IN (
    SELECT p.id FROM parejas p
    WHERE (p.jugador1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = p.jugador1_id))
       OR (p.jugador2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = p.jugador2_id))
)
OR ganador_id IN (
    SELECT p.id FROM parejas p
    WHERE (p.jugador1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = p.jugador1_id))
       OR (p.jugador2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = p.jugador2_id))
);

DELETE FROM parejas
WHERE (jugador1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = jugador1_id))
   OR (jugador2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM jugadores j WHERE j.id = jugador2_id));
