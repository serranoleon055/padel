package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.enums.Genero;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    // Filtrar por genero
    List<Categoria> findByGenero(Genero genero);

    // Verifica si una categoría ya existe antes de crearla
    Optional<Categoria> findByNombreAndGenero(String nombre, Genero genero);

}
