package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;

public interface ConfiguracionCategoriaTorneoRepository extends JpaRepository<ConfiguracionCategoriaTorneo, Long> {

    List<ConfiguracionCategoriaTorneo> findByTorneoId(Long torneoId);

    Optional<ConfiguracionCategoriaTorneo> findByTorneoIdAndCategoriaId(Long torneoId, Long categoriaId);
}
