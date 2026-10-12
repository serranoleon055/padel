package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.GastoTorneo;

public interface GastoTorneoRepository extends JpaRepository<GastoTorneo, Long> {
    List<GastoTorneo> findByTorneoIdOrderByFechaAscIdAsc(Long torneoId);
}
