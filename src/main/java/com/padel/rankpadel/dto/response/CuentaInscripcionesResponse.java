package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.padel.rankpadel.enums.EstadoPareja;
import com.padel.rankpadel.enums.MedioPago;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Quién pagó la inscripción de un torneo y quién debe, jugador por jugador. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuentaInscripcionesResponse {

    private Long torneoId;
    private BigDecimal totalEsperado;
    private BigDecimal totalCobrado;
    private BigDecimal totalPendiente;
    private int jugadoresConDeuda;
    private int jugadoresAlDia;
    private List<CuentaPareja> parejas;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CuentaPareja {
        private Long parejaId;
        private Long categoriaId;
        private String categoriaNombre;
        private EstadoPareja estado;
        private BigDecimal saldo;
        private List<CuentaJugador> jugadores;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CuentaJugador {
        private Long jugadorId;
        private String nombre;
        private String telefono;
        private BigDecimal costo;
        private BigDecimal pagado;
        private BigDecimal saldo;
        private List<Pago> pagos;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pago {
        private Long id;
        private LocalDate fecha;
        private BigDecimal monto;
        private MedioPago medio;
        private String notas;
        private boolean online;
        private String registradoPor;
        private LocalDateTime creadoEn;
        private boolean anulado;
        private String anuladoPor;
        private String motivoAnulacion;
    }
}
