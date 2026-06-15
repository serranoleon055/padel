package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.PosicionGrupo;

public interface PosicionGrupoRepository extends JpaRepository<PosicionGrupo, Long> {

    Optional<PosicionGrupo> findByGrupoIdAndParejaId(Long grupoId, Long parejaId);

    List<PosicionGrupo> findByGrupoId(Long grupoId);

    @Query("SELECT p FROM PosicionGrupo p WHERE p.grupo.id = :grupoId ORDER BY p.puntos DESC, (p.setsGanados - p.setsPerdidos) DESC, p.setsGanados DESC, (p.juegosGanados - p.juegosPerdidos) DESC")
    List<PosicionGrupo> findByGrupoIdOrdenado(@Param("grupoId") Long grupoId);

}
