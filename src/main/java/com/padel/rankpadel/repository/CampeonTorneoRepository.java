package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.padel.rankpadel.entity.CampeonTorneo;

public interface CampeonTorneoRepository extends JpaRepository<CampeonTorneo, Long> {

    List<CampeonTorneo> findByTorneoId(Long torneoId);

    void deleteByTorneoId(Long torneoId);

    boolean existsByTorneoIdAndCategoriaId(Long torneoId, Long categoriaId);

    @Query("""
        SELECT c FROM CampeonTorneo c
        WHERE c.torneo.activo = true
        ORDER BY c.fechaCoronacion DESC NULLS LAST, c.id DESC
        """)
    List<CampeonTorneo> findAllOrdenados();

}
