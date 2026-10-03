package com.padel.rankpadel.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    /**
     * El kardex de un producto, del más nuevo al más viejo.
     *
     * <p>El desempate por id no es decorativo: una venta y su anulación pueden quedar con
     * el mismo instante, y sin un orden estable el saldo de esas dos filas se dibuja
     * distinto en cada consulta. Es el mismo orden que usa {@link #saldoHasta}.
     */
    @Query(value = """
        SELECT m FROM MovimientoStock m
        JOIN FETCH m.producto
        WHERE m.producto.id = :productoId
          AND (:desde IS NULL OR m.fecha >= :desde)
          AND (:hasta IS NULL OR m.fecha <= :hasta)
        ORDER BY m.fecha DESC, m.id DESC
        """,
        countQuery = """
        SELECT COUNT(m) FROM MovimientoStock m
        WHERE m.producto.id = :productoId
          AND (:desde IS NULL OR m.fecha >= :desde)
          AND (:hasta IS NULL OR m.fecha <= :hasta)
        """)
    Page<MovimientoStock> findDelProducto(@Param("productoId") Long productoId,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            Pageable pageable);

    /**
     * Las unidades que había después de ese movimiento: la suma de todo lo anterior,
     * incluido él.
     *
     * <p>Se calcula para UNA fila —la más vieja de la página— y el resto se acumula sobre
     * ella. Pedirle el saldo a la base fila por fila serían veinte consultas por página, y
     * una función de ventana sobre todo el historial lo recorre entero cada vez que
     * alguien pasa de página.
     *
     * <p>**Ignora el filtro de fechas a propósito.** El saldo es cuántas unidades había,
     * no cuántas hubo dentro del rango que se eligió mirar: con el filtro aplicado, un
     * kardex del último mes arrancaría en cero y diría que el depósito estaba vacío.
     */
    @Query("""
        SELECT COALESCE(SUM(m.cantidad), 0) FROM MovimientoStock m
        WHERE m.producto.id = :productoId
          AND (m.fecha < :fecha OR (m.fecha = :fecha AND m.id <= :id))
        """)
    int saldoHasta(@Param("productoId") Long productoId,
            @Param("fecha") LocalDateTime fecha, @Param("id") Long id);

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
