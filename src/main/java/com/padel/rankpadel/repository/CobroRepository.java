package com.padel.rankpadel.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Cobro;

public interface CobroRepository extends JpaRepository<Cobro, Long> {

    List<Cobro> findByReservaIdOrderByCobradoEnAsc(Long reservaId);

    @Query("SELECT COALESCE(SUM(c.monto), 0) FROM Cobro c WHERE c.reserva.id = :reservaId")
    BigDecimal totalCobradoDe(@Param("reservaId") Long reservaId);

    /**
     * Cobrado por reserva en una sola consulta: el listado de turnos del día muestra el
     * saldo de cada uno y no puede disparar una consulta por fila.
     */
    @Query("SELECT c.reserva.id AS reservaId, SUM(c.monto) AS total "
            + "FROM Cobro c WHERE c.reserva.id IN :reservaIds GROUP BY c.reserva.id")
    List<TotalPorReserva> totalesPorReserva(@Param("reservaIds") List<Long> reservaIds);

    @Query("""
        SELECT c FROM Cobro c
        JOIN FETCH c.reserva r
        LEFT JOIN FETCH r.cancha
        WHERE c.cobradoEn >= :desde AND c.cobradoEn < :hasta
        ORDER BY c.cobradoEn
        """)
    List<Cobro> findDelDia(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    interface TotalPorReserva {
        Long getReservaId();

        BigDecimal getTotal();
    }
}
