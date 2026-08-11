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

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class IngresoMes {
        private String mes;
        private BigDecimal turnos;
        private BigDecimal inscripciones;
        /** Egresos del mes: sin esto el gráfico muestra facturación, no rentabilidad. */
        private BigDecimal egresos;
        /** Ingresos menos egresos. */
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
        private Integer cupo;
        private BigDecimal ingresos;
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
