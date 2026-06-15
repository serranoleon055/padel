package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Cancha;

public interface CanchaRepository extends JpaRepository<Cancha, Long> {

    List<Cancha> findByLugarIdAndActivoTrue(Long lugarId);

    List<Cancha> findByActivoTrue();

}
