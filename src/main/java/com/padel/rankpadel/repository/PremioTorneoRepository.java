package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.PremioTorneo;

public interface PremioTorneoRepository extends JpaRepository<PremioTorneo, Long> {
    List<PremioTorneo> findByTorneoIdOrderByIdAsc(Long torneoId);
}
