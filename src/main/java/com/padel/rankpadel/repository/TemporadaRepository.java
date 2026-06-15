package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Temporada;

public interface TemporadaRepository extends JpaRepository<Temporada, Long> {

    Optional<Temporada> findFirstByActivaTrue();

    List<Temporada> findByArchivadoFalse();

}
