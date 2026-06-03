package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.RondaEliminatorias;

@Repository
public interface RondaEliminatoriasRepository extends JpaRepository<RondaEliminatorias, Long> {

    // Filtra por torneo y ordena por el campo orden
    List<RondaEliminatorias> findByTorneoIdOrderByOrden(Long torneoId);

    // Fuktra por torneoid categoriaid y ordena por el campo orden
    List<RondaEliminatorias> findByTorneoIdAndCategoriaIdOrderByOrden(Long torneoId, Long categoriaId);

}
