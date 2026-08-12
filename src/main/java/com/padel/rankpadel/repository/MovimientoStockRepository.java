package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    @Query("""
        SELECT m FROM MovimientoStock m
        JOIN FETCH m.producto
        WHERE m.producto.id = :productoId
        ORDER BY m.fecha DESC
        """)
    List<MovimientoStock> findDelProducto(@Param("productoId") Long productoId, Pageable pageable);

    /**
     * Las entradas de mercadería, de todos los productos. Es el historial de compras del
     * club: sin esto había que abrir producto por producto para saber a quién se le
     * compró y a cuánto.
     */
    @Query("""
        SELECT m FROM MovimientoStock m
        JOIN FETCH m.producto
        WHERE m.motivo = com.padel.rankpadel.enums.MotivoMovimientoStock.COMPRA
        ORDER BY m.fecha DESC
        """)
    List<MovimientoStock> findCompras(Pageable pageable);

    List<MovimientoStock> findByVentaId(Long ventaId);
}
