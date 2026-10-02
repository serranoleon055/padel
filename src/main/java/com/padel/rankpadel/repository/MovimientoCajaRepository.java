package com.padel.rankpadel.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.MovimientoCaja;
import com.padel.rankpadel.enums.ConceptoMovimientoCaja;
import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoMovimientoCaja;

/**
 * Todas las consultas filtran {@code anuladoEn IS NULL}: un movimiento anulado queda en la
 * tabla para poder auditarlo pero no suma en ningún total. Si se agrega una consulta acá,
 * ese filtro va sí o sí, o el efectivo esperado miente.
 */
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    @Query("""
            SELECT m FROM MovimientoCaja m
            WHERE m.jornada = :jornada AND m.anuladoEn IS NULL
            ORDER BY m.fecha ASC, m.id ASC
            """)
    List<MovimientoCaja> findDeLaJornada(@Param("jornada") LocalDate jornada);

    @Query("""
            SELECT m FROM MovimientoCaja m
            WHERE m.jornada = :jornada AND m.anuladoEn IS NOT NULL
            ORDER BY m.fecha ASC, m.id ASC
            """)
    List<MovimientoCaja> findAnuladosDeLaJornada(@Param("jornada") LocalDate jornada);

    /** Si la caja de esta jornada ya se abrió. Solo se abre una vez por jornada. */
    boolean existsByJornadaAndConceptoAndAnuladoEnIsNull(LocalDate jornada,
            ConceptoMovimientoCaja concepto);

    @Query("""
            SELECT m FROM MovimientoCaja m
            WHERE m.jornada = :jornada AND m.concepto = :concepto AND m.anuladoEn IS NULL
            ORDER BY m.id ASC
            """)
    List<MovimientoCaja> findDeLaJornadaPorConcepto(@Param("jornada") LocalDate jornada,
            @Param("concepto") ConceptoMovimientoCaja concepto);

    /**
     * Totales por tipo y medio de una jornada, agrupados en la base. Es lo que necesita el
     * arqueo: traer las filas para sumarlas en Java sería hacerle a mano el trabajo al
     * motor.
     */
    @Query("""
            SELECT m.tipo AS tipo, m.medio AS medio, COALESCE(SUM(m.monto), 0) AS total,
                   COUNT(m) AS cantidad
            FROM MovimientoCaja m
            WHERE m.jornada = :jornada AND m.anuladoEn IS NULL
            GROUP BY m.tipo, m.medio
            """)
    List<TotalPorTipoYMedio> totalesDeLaJornada(@Param("jornada") LocalDate jornada);

    interface TotalPorTipoYMedio {
        TipoMovimientoCaja getTipo();

        MedioPago getMedio();

        BigDecimal getTotal();

        long getCantidad();
    }
}
