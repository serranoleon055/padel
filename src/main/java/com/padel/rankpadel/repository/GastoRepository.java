package com.padel.rankpadel.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.enums.MedioPago;

/**
 * Todas las consultas filtran {@code anuladoEn IS NULL}: un gasto anulado sigue en la
 * tabla para poder auditarlo, pero no suma en ningún total. Si se agrega una consulta
 * acá, ese filtro va sí o sí, o el resultado del mes miente.
 *
 * <p>Y hay dos criterios de fecha a propósito, que no son intercambiables: la caja
 * pregunta por {@code jornada} (el día en que la plata salió del cajón) y el estado de
 * resultados por {@code fecha} (la fecha contable que eligió la persona).
 */
public interface GastoRepository extends JpaRepository<Gasto, Long> {

    /** Los egresos de una jornada, para el detalle del arqueo. */
    @Query("SELECT g FROM Gasto g WHERE g.jornada = :jornada AND g.anuladoEn IS NULL ORDER BY g.id ASC")
    List<Gasto> findDeLaJornada(@Param("jornada") LocalDate jornada);

    /** Los anulados de la jornada, que el cierre muestra aparte. */
    @Query("SELECT g FROM Gasto g WHERE g.jornada = :jornada AND g.anuladoEn IS NOT NULL ORDER BY g.id ASC")
    List<Gasto> findAnuladosDeLaJornada(@Param("jornada") LocalDate jornada);

    @Query("""
            SELECT g FROM Gasto g
            WHERE g.fecha BETWEEN :desde AND :hasta AND g.anuladoEn IS NULL
            ORDER BY g.fecha DESC
            """)
    List<Gasto> findEntreFechas(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /**
     * Lo que salió del cajón en una jornada por un medio. Es lo que el arqueo resta del
     * efectivo esperado: si se le pagó al gasista del cajón, esa plata ya no está.
     */
    @Query("""
            SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g
            WHERE g.jornada = :jornada AND g.medio = :medio AND g.anuladoEn IS NULL
            """)
    BigDecimal totalDeLaJornadaPorMedio(@Param("jornada") LocalDate jornada,
            @Param("medio") MedioPago medio);

    /**
     * Los egresos que SALIERON de la caja en la jornada. Excluye los que todavía no se
     * pagaron (medio nulo = compra a cuenta corriente del proveedor): la mercadería entró
     * y pesa en la rentabilidad del mes, pero la plata no se movió. Es la misma distinción
     * entre devengado y caja que ya rige del lado de los ingresos.
     */
    @Query("""
            SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g
            WHERE g.jornada = :jornada AND g.medio IS NOT NULL AND g.anuladoEn IS NULL
            """)
    BigDecimal totalDeLaJornada(@Param("jornada") LocalDate jornada);
}
