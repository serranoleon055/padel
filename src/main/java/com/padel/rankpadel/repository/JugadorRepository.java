package com.padel.rankpadel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.Jugador;
import java.util.List;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.entity.Categoria;

@Repository
public interface JugadorRepository extends JpaRepository<Jugador, Long> {

    List<Jugador> findByGenero(Genero genero);

    List<Jugador> findByActivoTrue();

    long countByActivoTrue();

    List<Jugador> findByCategoria(Categoria categoria);

    List<Jugador> findByCategoriaAndGenero(Categoria categoria, Genero genero);

    List<Jugador> findByNombreContainingIgnoreCase(String texto);

    @Query("SELECT j FROM Jugador j LEFT JOIN FETCH j.categoria WHERE j.activo = true")
    List<Jugador> findAllConCategoria();

}
