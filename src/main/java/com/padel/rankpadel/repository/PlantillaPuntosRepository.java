package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.PlantillaPuntos;

public interface PlantillaPuntosRepository extends JpaRepository<PlantillaPuntos, Long> {

    List<PlantillaPuntos> findAllByOrderByNombreAsc();

    List<PlantillaPuntos> findByActivoTrueOrderByNombreAsc();

    Optional<PlantillaPuntos> findByIdAndActivoTrue(Long id);
}
