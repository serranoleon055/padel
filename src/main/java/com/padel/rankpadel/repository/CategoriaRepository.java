package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.enums.Genero;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findByGenero(Genero genero);

    Optional<Categoria> findByNombreAndGenero(String nombre, Genero genero);

    List<Categoria> findByArchivadoFalse();

}
