package com.padel.rankpadel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.RankingEntry;
import java.util.List;
import java.util.Optional;

import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.enums.Genero;

@Repository
public interface RankingEntryRepository extends JpaRepository<RankingEntry, Long> {

    // Categoria y temporada especifica
    List<RankingEntry> findByCategoriaAndTemporada(Categoria categoria, Temporada temporada);

    // Para saber si hay que crear o no el jugador
    Optional<RankingEntry> findByJugadorIdAndCategoriaIdAndTemporadaId(Long jugadorId, Long categoriaId,
            Long temporadaId);

    // Filtrar por id de jugador para perfil
    List<RankingEntry> findByJugadorId(Long jugadorId);

    Optional<RankingEntry> findByJugadorIdAndCategoriaIdAndTemporadaIsNull(Long jugadorId, Long categoriaId);

    List<RankingEntry> findByCategoriaIdAndTemporadaIsNull(Long categoriaId);

    List<RankingEntry> findByCategoriaGeneroAndTemporadaIsNull(Genero genero);

    List<RankingEntry> findByTemporadaIsNull();

    List<RankingEntry> findByCategoriaIdAndTemporadaId(Long categoriaId, Long temporadaId);

    List<RankingEntry> findByCategoriaGeneroAndTemporadaId(Genero genero, Long temporadaId);

    List<RankingEntry> findByTemporadaId(Long temporadaId);

}
