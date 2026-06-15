package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.BloqueoCancha;

public interface BloqueoCanchaRepository extends JpaRepository<BloqueoCancha, Long> {

    List<BloqueoCancha> findByCanchaId(Long canchaId);
}
