package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.RondaEliminatorias;

public interface RondaEliminatoriasRepository extends JpaRepository<RondaEliminatorias, Long> {

    List<RondaEliminatorias> findByTorneoIdOrderByOrden(Long torneoId);

    List<RondaEliminatorias> findByTorneoIdAndCategoriaIdOrderByOrden(Long torneoId, Long categoriaId);

}
