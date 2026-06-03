package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.Grupo;

@Repository
public interface GrupoRepository extends JpaRepository<Grupo, Long> {

    // Filtra por torneoid
    List<Grupo> findByTorneoId(Long torneoId);

    // Filtra por torneoid y cateogoriaid
    List<Grupo> findByTorneoIdAndCategoriaId(Long torneoId, Long categoriaId);

}
