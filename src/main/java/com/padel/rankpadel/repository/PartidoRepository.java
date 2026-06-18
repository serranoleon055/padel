package com.padel.rankpadel.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;

public interface PartidoRepository extends JpaRepository<Partido, Long> {

    List<Partido> findByTorneoId(Long torneoId);

    List<Partido> findByTorneoIdAndFase(Long torneoId, FasePartido fase);

    List<Partido> findByGrupoId(Long grupoId);

    List<Partido> findByRondaId(Long rondaId);

    List<Partido> findByRondaIdOrderByOrdenLlaveAscIdAsc(Long rondaId);

    List<Partido> findByRondaIdAndEstado(Long rondaId, EstadoPartido estado);

    long countByTorneoIdAndFaseAndEstado(Long torneoId, FasePartido fase, EstadoPartido estado);

    long countByRondaIdAndEstado(Long rondaId, EstadoPartido estado);

    long countByEstado(EstadoPartido estado);

    long countByTorneoId(Long torneoId);

    long countByTorneoIdAndEstado(Long torneoId, EstadoPartido estado);

    List<Partido> findByCanchaIdAndFechaHoraProgramadaBetween(Long canchaId, java.time.LocalDateTime desde, java.time.LocalDateTime hasta);

    @Query("""
        SELECT p FROM Partido p
        WHERE p.estado = :estado
          AND p.torneo.activo = true
        ORDER BY p.fechaHora DESC NULLS LAST, p.id DESC
        LIMIT 10
        """)
    List<Partido> findTop10ByEstadoOrderByFechaHoraDescIdDesc(@Param("estado") EstadoPartido estado);

    @Query("""
        SELECT p FROM Partido p
        WHERE p.torneo.id = :torneoId
          AND p.fechaHoraProgramada IS NOT NULL
          AND p.estado NOT IN ('FINALIZADO','BYE','WALKOVER','RETIRO')
        ORDER BY p.fechaHoraProgramada ASC
        """)
    List<Partido> findCalendarioPorTorneo(@Param("torneoId") Long torneoId);

    @Query("""
        SELECT p FROM Partido p
        WHERE p.estado = 'PENDIENTE'
          AND p.fechaHoraProgramada IS NOT NULL
          AND p.fechaHoraProgramada <= :ahora
          AND p.torneo.estado = 'EN_CURSO'
          AND p.torneo.activo = true
        """)
    List<Partido> findParaIniciarAutomatico(@Param("ahora") java.time.LocalDateTime ahora);

    @Query("""
        SELECT p FROM Partido p
        WHERE p.fechaHoraProgramada >= :desde
          AND p.estado NOT IN ('FINALIZADO','BYE','WALKOVER','RETIRO')
          AND p.torneo.activo = true
        ORDER BY p.fechaHoraProgramada ASC
        """)
    List<Partido> findProximos(@Param("desde") java.time.LocalDateTime desde);

    @Query("""
        SELECT p FROM Partido p
        WHERE p.estado = 'FINALIZADO'
          AND p.ronda IS NOT NULL
          AND LOWER(p.ronda.nombre) = 'final'
          AND p.ganador IS NOT NULL
          AND p.torneo.activo = true
        ORDER BY p.id DESC
        """)
    List<Partido> findUltimasFinales();

    @Query("""
        SELECT p FROM Partido p
        WHERE p.estado = 'FINALIZADO'
          AND p.ronda IS NOT NULL
          AND LOWER(p.ronda.nombre) = 'final'
          AND p.ganador IS NOT NULL
          AND p.torneo.activo = true
        ORDER BY p.fechaHora DESC NULLS LAST, p.id DESC
        """)
    List<Partido> findCampeones();

    @Query("""
        SELECT p FROM Partido p
        WHERE p.estado IN ('FINALIZADO', 'RETIRO')
          AND p.ganador IS NOT NULL
          AND p.torneo.activo = true
          AND p.torneo.sumaPuntosRanking = true
        ORDER BY p.id ASC
        """)
    List<Partido> findPartidosQueSumanPuntos();

    @Query("""
        SELECT p FROM Partido p
        WHERE (p.estado = 'FINALIZADO' OR p.estado = 'WALKOVER' OR p.estado = 'RETIRO')
          AND p.torneo.activo = true
          AND (
            (p.local IS NOT NULL AND (p.local.jugador1.id = :jugadorId OR p.local.jugador2.id = :jugadorId))
            OR
            (p.visitante IS NOT NULL AND (p.visitante.jugador1.id = :jugadorId OR p.visitante.jugador2.id = :jugadorId))
          )
        ORDER BY p.fechaHora DESC NULLS LAST, p.id DESC
        """)
    List<Partido> findPartidosFinalizadosByJugadorId(@Param("jugadorId") Long jugadorId);

    @Query("""
        SELECT p FROM Partido p
        LEFT JOIN FETCH p.local
        LEFT JOIN FETCH p.visitante
        WHERE p.estado IN :estados
          AND p.torneo.activo = true
          AND ((p.local IS NOT NULL AND p.local.categoria.id IN :categoriaIds)
            OR (p.visitante IS NOT NULL AND p.visitante.categoria.id IN :categoriaIds))
        """)
    List<Partido> findJugadosPorCategorias(@Param("categoriaIds") Collection<Long> categoriaIds,
                                           @Param("estados") List<EstadoPartido> estados);

    @Query("""
        SELECT p FROM Partido p
        LEFT JOIN FETCH p.local
        LEFT JOIN FETCH p.visitante
        WHERE p.estado IN :estados
          AND p.torneo.activo = true
          AND p.torneo.temporada.id = :temporadaId
          AND ((p.local IS NOT NULL AND p.local.categoria.id IN :categoriaIds)
            OR (p.visitante IS NOT NULL AND p.visitante.categoria.id IN :categoriaIds))
        """)
    List<Partido> findJugadosPorCategoriasYTemporada(@Param("categoriaIds") Collection<Long> categoriaIds,
                                                     @Param("temporadaId") Long temporadaId,
                                                     @Param("estados") List<EstadoPartido> estados);

}
