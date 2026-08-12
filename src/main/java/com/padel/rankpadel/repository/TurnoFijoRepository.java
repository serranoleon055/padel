package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.TurnoFijo;

public interface TurnoFijoRepository extends JpaRepository<TurnoFijo, Long> {

    @Query("""
        SELECT t FROM TurnoFijo t
        JOIN FETCH t.cancha c
        LEFT JOIN FETCH c.lugar
        WHERE t.activo = true
        ORDER BY t.diaSemana, t.horaInicio
        """)
    List<TurnoFijo> findActivosParaGenerar();

    @Query("""
        SELECT t FROM TurnoFijo t
        JOIN FETCH t.cancha c
        LEFT JOIN FETCH c.lugar l
        WHERE (:lugarId IS NULL OR l.id = :lugarId)
          AND (:canchaId IS NULL OR c.id = :canchaId)
        ORDER BY t.activo DESC, t.diaSemana, t.horaInicio
        """)
    List<TurnoFijo> buscar(Long lugarId, Long canchaId);

    /**
     * Abonos de una ficha. Antes se buscaban comparando el teléfono como texto, y un
     * abono cargado con guiones no aparecía en la ficha del mismo cliente.
     */
    @Query("""
        SELECT t FROM TurnoFijo t
        JOIN FETCH t.cancha c
        LEFT JOIN FETCH c.lugar
        WHERE t.cliente.id = :clienteId
        ORDER BY t.activo DESC, t.diaSemana, t.horaInicio
        """)
    List<TurnoFijo> findPorCliente(@Param("clienteId") Long clienteId);

    /** La generación corre fuera de transacción: la cancha tiene que venir ya cargada. */
    @Query("SELECT t FROM TurnoFijo t JOIN FETCH t.cancha c LEFT JOIN FETCH c.lugar WHERE t.id = :id")
    Optional<TurnoFijo> findByIdConCancha(@Param("id") Long id);

    /** Abonos vigentes de la misma cancha y el mismo día, para detectar superposiciones. */
    @Query("""
        SELECT t FROM TurnoFijo t
        JOIN FETCH t.cancha c
        WHERE t.activo = true
          AND c.id = :canchaId
          AND t.diaSemana = :diaSemana
          AND (:idExcluido IS NULL OR t.id <> :idExcluido)
        """)
    List<TurnoFijo> findActivosDelDia(@Param("canchaId") Long canchaId,
            @Param("diaSemana") int diaSemana,
            @Param("idExcluido") Long idExcluido);
}
