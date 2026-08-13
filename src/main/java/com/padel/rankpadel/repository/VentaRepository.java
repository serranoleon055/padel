package com.padel.rankpadel.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Venta;

/**
 * Todas las consultas excluyen las ventas anuladas. La anulación es baja lógica: la venta
 * sale de la caja, de las estadísticas y de la cuenta del turno, pero la fila queda para
 * poder explicar después qué se anuló y quién lo hizo. La única que las devuelve es
 * {@link #findAnuladasDelDia}.
 */
public interface VentaRepository extends JpaRepository<Venta, Long> {

    /**
     * Ventas del día con sus renglones y productos ya cargados: la caja las lista con el
     * detalle y no puede disparar una consulta por venta.
     */
    @Query("""
        SELECT DISTINCT v FROM Venta v
        LEFT JOIN FETCH v.items i
        LEFT JOIN FETCH i.producto
        WHERE v.fecha >= :desde AND v.fecha < :hasta AND v.anuladoEn IS NULL
        ORDER BY v.fecha
        """)
    List<Venta> findDelDiaConItems(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Las anuladas del día, para la solapa de anulados del cierre. */
    @Query("""
        SELECT DISTINCT v FROM Venta v
        LEFT JOIN FETCH v.items i
        LEFT JOIN FETCH i.producto
        WHERE v.fecha >= :desde AND v.fecha < :hasta AND v.anuladoEn IS NOT NULL
        ORDER BY v.fecha
        """)
    List<Venta> findAnuladasDelDia(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v "
            + "WHERE v.fecha >= :desde AND v.fecha < :hasta AND v.anuladoEn IS NULL")
    BigDecimal totalEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
        SELECT COALESCE(SUM(v.total), 0) FROM Venta v
        WHERE v.fecha >= :desde AND v.fecha < :hasta AND v.medio = :medio AND v.anuladoEn IS NULL
        """)
    BigDecimal totalEntrePorMedio(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            @Param("medio") com.padel.rankpadel.enums.MedioPago medio);

    /** Facturación de mostrador por mes, para sumarla al panel de rentabilidad. */
    @Query("""
        SELECT FUNCTION('DATE_FORMAT', v.fecha, '%Y-%m') AS mes, COALESCE(SUM(v.total), 0) AS total
        FROM Venta v WHERE v.fecha >= :desde AND v.anuladoEn IS NULL
        GROUP BY FUNCTION('DATE_FORMAT', v.fecha, '%Y-%m')
        """)
    List<TotalPorMes> totalPorMes(@Param("desde") LocalDateTime desde);

    /**
     * Costo de la mercadería VENDIDA por mes, con el costo congelado en cada renglón.
     *
     * <p>Es lo que se resta de los ingresos para llegar a la ganancia bruta. Ojo: NO es lo
     * mismo que la mercadería comprada en el mes —eso es inventario y vive en el capital
     * en stock hasta que se venda—. Confundirlos hace que un mes con una compra grande se
     * vea en rojo aunque no se haya vendido nada todavía.
     */
    @Query("""
        SELECT FUNCTION('DATE_FORMAT', v.fecha, '%Y-%m') AS mes,
               COALESCE(SUM(COALESCE(i.costoUnitario, 0) * i.cantidad), 0) AS total
        FROM VentaItem i JOIN i.venta v
        WHERE v.fecha >= :desde AND v.anuladoEn IS NULL
        GROUP BY FUNCTION('DATE_FORMAT', v.fecha, '%Y-%m')
        """)
    List<TotalPorMes> costoMercaderiaVendidaPorMes(@Param("desde") LocalDateTime desde);

    /**
     * Ranking de productos en un período: unidades, facturación y ganancia. Todo en una
     * consulta agrupada; recorrer las ventas en Java sería un N+1 disfrazado.
     */
    @Query("""
        SELECT p.id AS productoId,
               p.nombre AS nombre,
               SUM(i.cantidad) AS unidades,
               SUM(i.precioUnitario * i.cantidad) AS facturado,
               SUM(COALESCE(i.precioUnitario - i.costoUnitario, 0) * i.cantidad) AS ganancia
        FROM VentaItem i JOIN i.venta v JOIN i.producto p
        WHERE v.fecha >= :desde AND v.fecha < :hasta AND v.anuladoEn IS NULL
        GROUP BY p.id, p.nombre
        ORDER BY SUM(i.precioUnitario * i.cantidad) DESC
        """)
    List<VentaPorProducto> rankingProductos(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    interface TotalPorMes {
        String getMes();

        BigDecimal getTotal();
    }

    interface VentaPorProducto {
        Long getProductoId();

        String getNombre();

        long getUnidades();

        BigDecimal getFacturado();

        BigDecimal getGanancia();
    }

    /** Ventas cargadas a un turno, para cobrarlas junto con la cancha. */
    @Query("SELECT v FROM Venta v WHERE v.reserva.id = :reservaId AND v.anuladoEn IS NULL "
            + "ORDER BY v.fecha ASC")
    List<Venta> findVigentesDeReserva(@Param("reservaId") Long reservaId);

    /**
     * Consumo impago de varios turnos, agrupado. Sin medio de pago la venta es deuda de
     * la cuenta del turno, no plata que ya entró.
     */
    @Query("""
        SELECT v.reserva.id AS reservaId, COALESCE(SUM(v.total), 0) AS total
        FROM Venta v
        WHERE v.medio IS NULL AND v.reserva.id IN :reservaIds AND v.anuladoEn IS NULL
        GROUP BY v.reserva.id
        """)
    List<ConsumoPorReserva> consumoACuentaDe(@Param("reservaIds") List<Long> reservaIds);

    interface ConsumoPorReserva {
        Long getReservaId();

        BigDecimal getTotal();
    }

    @Query("SELECT v FROM Venta v WHERE v.cliente.id = :clienteId AND v.fecha > :desde "
            + "AND v.anuladoEn IS NULL ORDER BY v.fecha DESC")
    List<Venta> findComprasDelCliente(@Param("clienteId") Long clienteId, @Param("desde") LocalDateTime desde);

    @Query("SELECT COUNT(v) FROM Venta v WHERE FUNCTION('DATE', v.fecha) = :fecha AND v.anuladoEn IS NULL")
    long cantidadDelDia(@Param("fecha") LocalDate fecha);
}
