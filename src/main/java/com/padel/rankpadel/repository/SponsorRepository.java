package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Sponsor;

public interface SponsorRepository extends JpaRepository<Sponsor, Long> {

    /**
     * Los que van en la franja pública: activos, de esta sede o de todas. Trae el lugar
     * en el mismo viaje porque la respuesta lo nombra.
     */
    @Query("""
        SELECT s FROM Sponsor s
        LEFT JOIN FETCH s.lugar l
        WHERE s.activo = true AND (:lugarId IS NULL OR l.id IS NULL OR l.id = :lugarId)
        ORDER BY s.orden, s.nombre
        """)
    List<Sponsor> findVisibles(@Param("lugarId") Long lugarId);

    @Query("SELECT s FROM Sponsor s LEFT JOIN FETCH s.lugar ORDER BY s.activo DESC, s.orden, s.nombre")
    List<Sponsor> findTodos();
}
