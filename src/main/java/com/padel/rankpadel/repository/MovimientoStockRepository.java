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
     *
     * <p>Van también las reversiones: una compra anulada deja su entrada y su
     * compensatorio, y mostrar solo la entrada diría que esa mercadería está, cuando el
     * stock ya la devolvió.
     */
    @Query("""
        SELECT m FROM MovimientoStock m
        JOIN FETCH m.producto
        WHERE m.motivo IN (
            com.padel.rankpadel.enums.MotivoMovimientoStock.COMPRA,
            com.padel.rankpadel.enums.MotivoMovimientoStock.ANULACION_COMPRA)
        ORDER BY m.fecha DESC
        """)
    List<MovimientoStock> findCompras(Pageable pageable);

    /** Los renglones de una compra: este movimiento ES el renglón, no hay tabla aparte. */
    @Query("""
        SELECT m FROM MovimientoStock m JOIN FETCH m.producto
        WHERE m.documentoCompra.id = :compraId
        ORDER BY m.id ASC
        """)
    List<MovimientoStock> findDeLaCompra(@org.springframework.data.repository.query.Param("compraId") Long compraId);
}
