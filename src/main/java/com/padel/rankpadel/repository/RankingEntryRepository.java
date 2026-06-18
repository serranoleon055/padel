package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.RankingEntry;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.enums.Genero;

public interface RankingEntryRepository extends JpaRepository<RankingEntry, Long> {

    List<RankingEntry> findByCategoriaAndTemporada(Categoria categoria, Temporada temporada);

    Optional<RankingEntry> findByJugadorIdAndCategoriaIdAndTemporadaId(Long jugadorId, Long categoriaId,
            Long temporadaId);

    @EntityGraph(attributePaths = { "jugador", "categoria", "temporada" })
    List<RankingEntry> findByJugadorId(Long jugadorId);

    Optional<RankingEntry> findByJugadorIdAndCategoriaIdAndTemporadaIsNull(Long jugadorId, Long categoriaId);

    @EntityGraph(attributePaths = { "jugador", "categoria", "temporada" })
    List<RankingEntry> findByCategoriaIdAndTemporadaIsNull(Long categoriaId);

    @EntityGraph(attributePaths = { "jugador", "categoria", "temporada" })
    List<RankingEntry> findByCategoriaGeneroAndTemporadaIsNull(Genero genero);

    @EntityGraph(attributePaths = { "jugador", "categoria", "temporada" })
    List<RankingEntry> findByTemporadaIsNull();

    @EntityGraph(attributePaths = { "jugador", "categoria", "temporada" })
    List<RankingEntry> findByCategoriaIdAndTemporadaId(Long categoriaId, Long temporadaId);

    @EntityGraph(attributePaths = { "jugador", "categoria", "temporada" })
    List<RankingEntry> findByCategoriaGeneroAndTemporadaId(Genero genero, Long temporadaId);

    @EntityGraph(attributePaths = { "jugador", "categoria", "temporada" })
    List<RankingEntry> findByTemporadaId(Long temporadaId);

}
