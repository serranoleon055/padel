package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Grupo;

public interface GrupoRepository extends JpaRepository<Grupo, Long> {

    List<Grupo> findByTorneoId(Long torneoId);

    List<Grupo> findByTorneoIdAndCategoriaId(Long torneoId, Long categoriaId);

}
