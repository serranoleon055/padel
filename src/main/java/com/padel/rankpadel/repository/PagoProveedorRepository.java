package com.padel.rankpadel.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.PagoProveedor;

public interface PagoProveedorRepository extends JpaRepository<PagoProveedor, Long> {

    @Query("""
            SELECT p FROM PagoProveedor p
            WHERE p.proveedor.id = :proveedorId AND p.anuladoEn IS NULL
            ORDER BY p.fecha ASC, p.id ASC
            """)
    List<PagoProveedor> findDe(@Param("proveedorId") Long proveedorId);

    @Query("""
            SELECT COALESCE(SUM(p.monto), 0) FROM PagoProveedor p
            WHERE p.proveedor.id = :proveedorId AND p.anuladoEn IS NULL
            """)
    BigDecimal totalPagadoA(@Param("proveedorId") Long proveedorId);

    @Query("""
            SELECT p.proveedor.id AS proveedorId, COALESCE(SUM(p.monto), 0) AS total
            FROM PagoProveedor p WHERE p.anuladoEn IS NULL
            GROUP BY p.proveedor.id
            """)
    List<DocumentoCompraRepository.TotalPorProveedor> totalesPagados();

    @Query("""
            SELECT COALESCE(SUM(p.monto), 0) FROM PagoProveedor p
            WHERE p.jornada = :jornada AND p.medio = :medio AND p.anuladoEn IS NULL
            """)
    BigDecimal totalDeLaJornadaPorMedio(@Param("jornada") java.time.LocalDate jornada,
            @Param("medio") com.padel.rankpadel.enums.MedioPago medio);

    @Query("""
            SELECT COALESCE(SUM(p.monto), 0) FROM PagoProveedor p
            WHERE p.jornada = :jornada AND p.anuladoEn IS NULL
            """)
    BigDecimal totalDeLaJornada(@Param("jornada") java.time.LocalDate jornada);
}
