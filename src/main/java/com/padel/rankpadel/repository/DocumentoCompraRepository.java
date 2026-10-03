package com.padel.rankpadel.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.DocumentoCompra;

/**
 * Todas las consultas de plata filtran {@code anuladoEn IS NULL}: una compra anulada
 * queda para poder auditarla pero no suma en ningún total ni en ninguna cuenta corriente.
 */
public interface DocumentoCompraRepository extends JpaRepository<DocumentoCompra, Long> {

    @Query("""
            SELECT c FROM DocumentoCompra c
            JOIN FETCH c.proveedor
            WHERE c.anuladoEn IS NULL
              AND (:proveedorId IS NULL OR c.proveedor.id = :proveedorId)
            ORDER BY c.fecha DESC, c.id DESC
            """)
    Page<DocumentoCompra> buscar(@Param("proveedorId") Long proveedorId, Pageable pageable);

    /** Las compras que quedaron a cuenta: las que mueven el saldo del proveedor. */
    @Query("""
            SELECT c FROM DocumentoCompra c
            WHERE c.proveedor.id = :proveedorId AND c.medio IS NULL AND c.anuladoEn IS NULL
            ORDER BY c.fecha ASC, c.id ASC
            """)
    List<DocumentoCompra> findACreditoDe(@Param("proveedorId") Long proveedorId);

    @Query("""
            SELECT COALESCE(SUM(c.total), 0) FROM DocumentoCompra c
            WHERE c.proveedor.id = :proveedorId AND c.medio IS NULL AND c.anuladoEn IS NULL
            """)
    BigDecimal totalACreditoDe(@Param("proveedorId") Long proveedorId);

    @Query("""
            SELECT c.proveedor.id AS proveedorId, COALESCE(SUM(c.total), 0) AS total
            FROM DocumentoCompra c
            WHERE c.medio IS NULL AND c.anuladoEn IS NULL
            GROUP BY c.proveedor.id
            """)
    List<TotalPorProveedor> totalesACredito();

    boolean existsByProveedorIdAndTipoComprobanteAndNumeroAndAnuladoEnIsNull(
            Long proveedorId, com.padel.rankpadel.enums.TipoComprobanteCompra tipo, String numero);

    @Query("""
            SELECT COALESCE(SUM(c.total), 0) FROM DocumentoCompra c
            WHERE c.jornada = :jornada AND c.anuladoEn IS NULL
            """)
    BigDecimal totalDeLaJornada(@Param("jornada") LocalDate jornada);

    interface TotalPorProveedor {
        Long getProveedorId();

        BigDecimal getTotal();
    }
}
