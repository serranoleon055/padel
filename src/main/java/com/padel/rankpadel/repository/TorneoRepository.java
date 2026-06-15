package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoTorneo;

public interface TorneoRepository extends JpaRepository<Torneo, Long> {

    List<Torneo> findByEstadoIn(List<EstadoTorneo> estados);

    List<Torneo> findByActivoTrue();

    List<Torneo> findByActivoTrueAndEstadoIn(List<EstadoTorneo> estados);

    long countByActivoTrue();

    long countByActivoTrueAndEstadoIn(List<EstadoTorneo> estados);

    long countByEstadoIn(List<EstadoTorneo> estados);

    List<Torneo> findByTemporada(Temporada temporada);

    List<Torneo> findByLugar(Lugar lugar);

    @Query("SELECT DISTINCT t FROM Torneo t LEFT JOIN FETCH t.temporada LEFT JOIN FETCH t.lugar LEFT JOIN FETCH t.categorias WHERE t.activo = true")
    List<Torneo> findAllConRelaciones();

}
