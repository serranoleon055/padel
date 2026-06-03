package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.PosicionGrupo;

@Repository
public interface PosicionGrupoRepository extends JpaRepository<PosicionGrupo, Long> {

    Optional<PosicionGrupo> findByGrupoIdAndParejaId(Long grupoId, Long parejaId);

    List<PosicionGrupo> findByGrupoId(Long grupoId);

    /**
     * Orden base por puntos y diferencia de sets. El desempate fino (resultado directo y diferencia
     * de juegos) se resuelve en Java con {@link com.padel.rankpadel.util.PosicionGrupoOrdenador}
     * porque el head-to-head depende del partido jugado entre las parejas empatadas.
     */
    @Query("SELECT p FROM PosicionGrupo p WHERE p.grupo.id = :grupoId ORDER BY p.puntos DESC, (p.setsGanados - p.setsPerdidos) DESC, p.setsGanados DESC, (p.juegosGanados - p.juegosPerdidos) DESC")
    List<PosicionGrupo> findByGrupoIdOrdenado(@Param("grupoId") Long grupoId);

}
