package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.PlantillaFormato;

public interface PlantillaFormatoRepository extends JpaRepository<PlantillaFormato, Long> {

    List<PlantillaFormato> findAllByOrderByNombreAsc();

    List<PlantillaFormato> findByActivoTrueOrderByNombreAsc();

    Optional<PlantillaFormato> findByIdAndActivoTrue(Long id);
}
