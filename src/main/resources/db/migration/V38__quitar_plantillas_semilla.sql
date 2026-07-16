DELETE FROM plantillas_puntos_rondas
WHERE plantilla_id IN (SELECT id FROM plantillas_puntos WHERE nombre = 'Ranking estandar');

DELETE FROM plantillas_puntos WHERE nombre = 'Ranking estandar';

DELETE FROM plantillas_formato
WHERE nombre IN (
    '11 parejas - grupos desparejos',
    '12 parejas - 4 grupos',
    '15 parejas - grupos con bye',
    '16 parejas - cuadro directo'
);
