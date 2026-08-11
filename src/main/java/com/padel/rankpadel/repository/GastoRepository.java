package com.padel.rankpadel.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.enums.MedioPago;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findByFechaOrderByIdAsc(LocalDate fecha);

    List<Gasto> findByFechaBetweenOrderByFechaDesc(LocalDate desde, LocalDate hasta);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.fecha = :fecha AND g.medio = :medio")
    BigDecimal totalDelDiaPorMedio(@Param("fecha") LocalDate fecha, @Param("medio") MedioPago medio);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.fecha = :fecha")
    BigDecimal totalDelDia(@Param("fecha") LocalDate fecha);
}
