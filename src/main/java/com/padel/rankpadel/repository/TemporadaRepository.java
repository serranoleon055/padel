package com.padel.rankpadel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.Temporada;

@Repository
public interface TemporadaRepository extends JpaRepository<Temporada, Long> {

    Optional<Temporada> findFirstByActivaTrue();

}
