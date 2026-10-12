package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.TorneoSede;

public interface TorneoSedeRepository extends JpaRepository<TorneoSede, Long> {

    List<TorneoSede> findByTorneoIdOrderByIdAsc(Long torneoId);

    Optional<TorneoSede> findByTorneoIdAndLugarId(Long torneoId, Long lugarId);

    boolean existsByTorneoIdAndLugarId(Long torneoId, Long lugarId);
}
