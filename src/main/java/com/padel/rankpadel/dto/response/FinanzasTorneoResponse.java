package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.padel.rankpadel.enums.CategoriaGastoTorneo;
import com.padel.rankpadel.enums.MedioPago;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** La plata de un torneo: el balance y cada premio, gasto y aporte de sponsor. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanzasTorneoResponse {

    private Balance balance;
    private List<Premio> premios;
    private List<Gasto> gastos;
    private List<Aporte> aportes;

    /**
     * Proyectado: con todo lo comprometido (lo que se debería cobrar y pagar). Real: solo
     * lo que ya entró y salió. La diferencia es lo que falta cobrar y pagar.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Balance {
        private BigDecimal inscripcionesEsperado;
        private BigDecimal inscripcionesCobrado;
        private BigDecimal sponsorsComprometido;
        private BigDecimal sponsorsCobrado;
        private BigDecimal clubesAcordado;
        private BigDecimal clubesPagado;
        private BigDecimal premiosTotal;
        private BigDecimal premiosEntregado;
        private BigDecimal gastos;
        private BigDecimal resultadoProyectado;
        private BigDecimal resultadoReal;
        private BigDecimal porCobrar;
        private BigDecimal porPagar;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Premio {
        private Long id;
        private Long categoriaId;
        private String categoriaNombre;
        private String puesto;
        private String descripcion;
        private BigDecimal monto;
        private boolean entregado;
        private LocalDateTime entregadoEn;
        private String entregadoA;
        /** La pareja que ganó ese puesto, si ya se sabe (campeón o subcampeón). */
        private String ganador;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Gasto {
        private Long id;
        private LocalDate fecha;
        private CategoriaGastoTorneo categoria;
        private String descripcion;
        private BigDecimal monto;
        private MedioPago medio;
        private String registradoPor;
        private boolean anulado;
        private String anuladoPor;
        private String motivoAnulacion;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Aporte {
        private Long id;
        private Long sponsorId;
        private String nombre;
        private BigDecimal monto;
        private String descripcion;
        private boolean cobrado;
        private LocalDate cobradoEn;
        private MedioPago medio;
    }
}
