package com.padel.rankpadel.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.CierreCaja;

public interface CierreCajaRepository extends JpaRepository<CierreCaja, Long> {

    Optional<CierreCaja> findByFecha(LocalDate fecha);

    boolean existsByFecha(LocalDate fecha);

    List<CierreCaja> findAllByOrderByFechaDesc(Pageable pageable);
}
