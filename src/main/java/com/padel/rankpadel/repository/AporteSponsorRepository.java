package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.AporteSponsor;

public interface AporteSponsorRepository extends JpaRepository<AporteSponsor, Long> {
    List<AporteSponsor> findByTorneoIdOrderByIdAsc(Long torneoId);
}
