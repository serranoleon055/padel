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
     * Ventas de una jornada con sus renglones y productos ya cargados: la caja las lista
     * con el detalle y no puede disparar una consulta por venta.
     *
     * <p>Por jornada, no por día de calendario: lo vendido a la 1 AM es de la noche que
     * arrancó ayer y va en ese arqueo. Ver V55.
     */
    @Query("""
        SELECT DISTINCT v FROM Venta v
        LEFT JOIN FETCH v.items i
        LEFT JOIN FETCH i.producto
        WHERE v.jornada = :jornada AND v.anuladoEn IS NULL
        ORDER BY v.fecha
        """)
    List<Venta> findDeLaJornadaConItems(@Param("jornada") LocalDate jornada);

    /** Las anuladas de la jornada, para la solapa de anulados del cierre. */
    @Query("""
        SELECT DISTINCT v FROM Venta v
        LEFT JOIN FETCH v.items i
        LEFT JOIN FETCH i.producto
        WHERE v.jornada = :jornada AND v.anuladoEn IS NOT NULL
        ORDER BY v.fecha
        """)
    List<Venta> findAnuladasDeLaJornada(@Param("jornada") LocalDate jornada);

    /**
     * Facturación de mostrador por mes, para sumarla al panel de rentabilidad.
     *
     * <p>Agrupa por JORNADA, no por {@code fecha}: una venta de la 1 de la mañana del día
     * 1 es de la noche del último día del mes anterior, y la caja ya la puso ahí. Con el
     * criterio de calendario, las estadísticas del mes y la suma de los arqueos no
     * cuadraban justo en las noches de fin de mes.
     */
    @Query("""
        SELECT FUNCTION('DATE_FORMAT', v.jornada, '%Y-%m') AS mes, COALESCE(SUM(v.total), 0) AS total
        FROM Venta v WHERE v.jornada >= :desde AND v.anuladoEn IS NULL
        GROUP BY FUNCTION('DATE_FORMAT', v.jornada, '%Y-%m')
        """)
    List<TotalPorMes> totalPorMes(@Param("desde") LocalDate desde);

    /**
     * Costo de la mercadería VENDIDA por mes, con el costo congelado en cada renglón.
     *
     * <p>Es lo que se resta de los ingresos para llegar a la ganancia bruta. Ojo: NO es lo
     * mismo que la mercadería comprada en el mes —eso es inventario y vive en el capital
     * en stock hasta que se venda—. Confundirlos hace que un mes con una compra grande se
     * vea en rojo aunque no se haya vendido nada todavía.
     */
    @Query("""
        SELECT FUNCTION('DATE_FORMAT', v.jornada, '%Y-%m') AS mes,
               COALESCE(SUM(COALESCE(i.costoUnitario, 0) * i.cantidad), 0) AS total
        FROM VentaItem i JOIN i.venta v
        WHERE v.jornada >= :desde AND v.anuladoEn IS NULL
        GROUP BY FUNCTION('DATE_FORMAT', v.jornada, '%Y-%m')
        """)
    List<TotalPorMes> costoMercaderiaVendidaPorMes(@Param("desde") LocalDate desde);

    /**
     * Ranking de productos en un período: unidades, facturación y ganancia. Todo en una
     * consulta agrupada; recorrer las ventas en Java sería un N+1 disfrazado.
     *
     * <p>Las unidades vendidas SIN costo cargado se cuentan aparte, en vez de aportar
     * ganancia cero. El costo es opcional —el club puede no saber todavía cuánto le sale
     * algo—, y con un {@code COALESCE} a cero un producto sin costo aparecía con ganancia
     * 0 y margen 0%, indistinguible de uno que se vende a pérdida, y arrastraba la
     * ganancia del kiosco para abajo. Ahora la ganancia es la de lo que SÍ tiene costo, y
     * {@code unidadesSinCosto} le dice al panel que ese número está incompleto.
     */
    @Query("""
        SELECT p.id AS productoId,
               p.nombre AS nombre,
               SUM(i.cantidad) AS unidades,
               SUM(i.precioUnitario * i.cantidad) AS facturado,
               SUM(CASE WHEN i.costoUnitario IS NULL THEN 0
                        ELSE (i.precioUnitario - i.costoUnitario) * i.cantidad END) AS ganancia,
               SUM(CASE WHEN i.costoUnitario IS NULL THEN i.cantidad ELSE 0 END) AS unidadesSinCosto,
               SUM(CASE WHEN i.costoUnitario IS NULL THEN 0
                        ELSE i.precioUnitario * i.cantidad END) AS facturadoConCosto
        FROM VentaItem i JOIN i.venta v JOIN i.producto p
        WHERE v.jornada >= :desde AND v.jornada <= :hasta AND v.anuladoEn IS NULL
        GROUP BY p.id, p.nombre
        ORDER BY SUM(i.precioUnitario * i.cantidad) DESC
        """)
    List<VentaPorProducto> rankingProductos(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    interface TotalPorMes {
        String getMes();

        BigDecimal getTotal();
    }

    interface VentaPorProducto {
        Long getProductoId();

        String getNombre();

        long getUnidades();

        BigDecimal getFacturado();

        /** Ganancia de lo que tiene costo cargado. */
        BigDecimal getGanancia();

        /** Unidades vendidas sin costo cargado: el margen de esas no se puede calcular. */
        long getUnidadesSinCosto();

        /** Facturación de lo que SÍ tiene costo, que es sobre lo que se mide el margen. */
        BigDecimal getFacturadoConCosto();
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

}
