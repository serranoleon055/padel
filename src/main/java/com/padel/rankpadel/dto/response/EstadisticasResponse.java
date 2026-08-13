package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EstadisticasResponse {

    private List<OcupacionFranja> heatmap;
    /**
     * Hora a la que arranca la jornada del club. El gráfico de horas se dibuja desde acá
     * hacia adelante: en un club que abre 10 y cierra 2, las 00 y la 1 son el final de la
     * noche y ordenarlas por número las ponía primero, antes de la hora de apertura.
     */
    private int horaApertura;
    private List<CanchaUso> canchasMasUsadas;
    private List<IngresoMes> ingresosPorMes;
    private long reservasTotales;
    private long reservasCanceladas;
    private double tasaCancelacion;
    private long reservasNoShow;
    /** Ausentes sobre los turnos que debieron jugarse (no sobre el total de solicitudes). */
    private double tasaNoShow;
    private List<EmbudoTorneo> embudoTorneos;
    private List<CategoriaDemanda> categoriasDemandadas;

    /** El resumen del mes en curso contra el anterior: ¿el club está creciendo o no? */
    private ResumenMes mesActual;
    /** Ocupación de cada cancha: horas vendidas sobre horas que el club tuvo abierto. */
    private List<OcupacionCancha> ocupacionPorCancha;
    /** Qué deja plata en el mostrador, ordenado por ganancia y no por facturación. */
    private List<ProductoRendimiento> rendimientoProductos;
    /** Quiénes sostienen el club: los que más gastaron en el período. */
    private List<ClienteTop> mejoresClientes;
    private Kiosco kiosco;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ResumenMes {
        private String mes;
        private BigDecimal facturado;
        private BigDecimal facturadoMesAnterior;
        /** Variación porcentual contra el mes anterior. Null si el anterior fue cero. */
        private Double variacion;
        private BigDecimal resultado;
        private long turnosJugados;
        /** Cancha + consumo promedio por turno: cuánto deja cada grupo que entra. */
        private BigDecimal ticketPromedio;
        /** Porcentaje de horas vendidas sobre las horas que el club estuvo abierto. */
        private double ocupacion;
        /** Lo que el club facturó por cada hora que tuvo la cancha abierta. */
        private BigDecimal ingresoPorHoraAbierta;
        /** Plata de turnos ya jugados que todavía no se cobró. */
        private BigDecimal deudaAcumulada;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class OcupacionCancha {
        private String canchaNombre;
        private long horasVendidas;
        private long horasDisponibles;
        private double ocupacion;
        private BigDecimal facturado;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ProductoRendimiento {
        private Long productoId;
        private String nombre;
        private long unidades;
        private BigDecimal facturado;
        /** Precio menos costo, con los valores congelados en cada venta. */
        private BigDecimal ganancia;
        /** Ganancia sobre facturación, en porcentaje. */
        private Double margen;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ClienteTop {
        private Long clienteId;
        private String nombre;
        private long turnos;
        private BigDecimal gastado;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Kiosco {
        private BigDecimal facturado;
        private BigDecimal ganancia;
        /** Plata parada en el depósito, valuada a costo. */
        private BigDecimal capitalEnStock;
        private long productosBajoMinimo;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class OcupacionFranja {
        private int diaSemana;
        private int hora;
        private long cantidad;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CanchaUso {
        private String canchaNombre;
        private long reservas;
    }

    /**
     * El estado de resultados del mes, con criterio devengado:
     *
     * <pre>
     *   ingresos            turnos + inscripciones + ventas
     * - costoMercaderia     costo congelado de lo VENDIDO (no de lo comprado)
     * = gananciaBruta
     * - gastosOperativos    luz, sueldos, mantenimiento... SIN las compras de mercadería
     * = resultado
     * </pre>
     *
     * <p>Las compras de mercadería quedan afuera de los gastos operativos a propósito: son
     * inventario, no gasto, y viven en el capital en stock hasta venderse. Contarlas ahí y
     * además restar el costo de lo vendido sería contar la misma plata dos veces.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class IngresoMes {
        private String mes;
        private BigDecimal turnos;
        private BigDecimal inscripciones;
        /** Lo vendido en el mostrador: pelotas, bebidas, alquiler de paletas. */
        private BigDecimal ventas;
        /** turnos + inscripciones + ventas. */
        private BigDecimal ingresos;
        /** Costo congelado de la mercadería vendida en el mes. */
        private BigDecimal costoMercaderia;
        /** Ingresos menos el costo de lo vendido. */
        private BigDecimal gananciaBruta;
        /** Gastos del mes SIN las compras de mercadería. */
        private BigDecimal gastosOperativos;
        /** Compras de mercadería del mes: no es gasto, es inventario. Se informa aparte. */
        private BigDecimal comprasMercaderia;
        /** Ganancia bruta menos gastos operativos. */
        private BigDecimal resultado;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EmbudoTorneo {
        private Long torneoId;
        private String torneoNombre;
        private long inscriptos;
        /**
         * Suma de los cupos de las categorías del torneo. Queda en null si alguna
         * categoría no tiene cupo definido: un total incompleto se leería como techo real.
         */
        private Integer cupo;
        private BigDecimal ingresos;
        /** El cupo se define por categoría, así que el detalle es lo que el club mira. */
        private List<CupoCategoria> categorias;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CupoCategoria {
        private Long categoriaId;
        private String categoriaNombre;
        private long inscriptos;
        private Integer cupo;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CategoriaDemanda {
        private String categoriaNombre;
        private long inscriptos;
    }
}
