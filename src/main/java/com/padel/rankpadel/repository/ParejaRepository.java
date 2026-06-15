package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.enums.EstadoTorneo;

public interface ParejaRepository extends JpaRepository<Pareja, Long> {

    List<Pareja> findByTorneoId(Long torneoId);

    long countByTorneoId(Long torneoId);

    List<Pareja> findByTorneoIdAndCategoriaId(Long torneoId, Long categoriaId);

    long countByTorneoIdAndCategoriaId(Long torneoId, Long categoriaId);

    List<Pareja> findByTorneoIdAndEsCabezaDeSerie(Long torneoId, boolean esCabezaDeSerie);

    List<Pareja> findByGrupoId(Long grupoId);

    @Query("SELECT COUNT(p) > 0 FROM Pareja p WHERE p.torneo.id = :torneoId AND p.categoria.id = :categoriaId AND (p.jugador1.id = :jugadorId OR p.jugador2.id = :jugadorId)")
    boolean jugadorYaInscriptoEnCategoria(@Param("torneoId") Long torneoId, @Param("categoriaId") Long categoriaId, @Param("jugadorId") Long jugadorId);

    @Query("""
            SELECT COUNT(DISTINCT p.torneo.id) FROM Pareja p
            WHERE p.categoria.id = :categoriaId
              AND (p.jugador1.id = :jugadorId OR p.jugador2.id = :jugadorId)
              AND p.torneo.estado IN :estados
            """)
    long contarTorneosJugados(@Param("jugadorId") Long jugadorId,
                              @Param("categoriaId") Long categoriaId,
                              @Param("estados") List<EstadoTorneo> estados);

    @Query("""
            SELECT COUNT(DISTINCT p.torneo.id) FROM Pareja p
            WHERE p.categoria.id = :categoriaId
              AND (p.jugador1.id = :jugadorId OR p.jugador2.id = :jugadorId)
              AND p.torneo.estado IN :estados
              AND p.torneo.temporada.id = :temporadaId
            """)
    long contarTorneosJugadosPorTemporada(@Param("jugadorId") Long jugadorId,
                                          @Param("categoriaId") Long categoriaId,
                                          @Param("temporadaId") Long temporadaId,
                                          @Param("estados") List<EstadoTorneo> estados);

}
