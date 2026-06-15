package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.HorarioCancha;

public interface HorarioCanchaRepository extends JpaRepository<HorarioCancha, Long> {

    List<HorarioCancha> findByCanchaIdAndActivoTrue(Long canchaId);

    List<HorarioCancha> findByCanchaId(Long canchaId);
}
