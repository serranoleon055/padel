package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Cancha;

public interface CanchaRepository extends JpaRepository<Cancha, Long> {

    List<Cancha> findByLugarIdAndActivoTrue(Long lugarId);

    /** Incluye las dadas de baja: lo usan el panel de sedes y la reactivación. */
    List<Cancha> findByLugarId(Long lugarId);

    List<Cancha> findByActivoTrue();

}
