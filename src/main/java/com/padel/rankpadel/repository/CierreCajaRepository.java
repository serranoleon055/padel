package com.padel.rankpadel.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.CierreCaja;

/**
 * Un arqueo anulado (reabierto) queda en la tabla, así que todo lo que pregunte "¿está
 * cerrada esta jornada?" tiene que filtrar por vigente. Lo que no filtra es el historial:
 * ahí los reabiertos son justamente lo que se quiere ver.
 */
public interface CierreCajaRepository extends JpaRepository<CierreCaja, Long> {

    Optional<CierreCaja> findByFechaAndAnuladoEnIsNull(LocalDate fecha);

    boolean existsByFechaAndAnuladoEnIsNull(LocalDate fecha);

    /** El historial completo, reaperturas incluidas. */
    Page<CierreCaja> findAllByOrderByFechaDescIdDesc(Pageable pageable);

    List<CierreCaja> findByFechaOrderByIdDesc(LocalDate fecha);
}
