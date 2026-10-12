package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.padel.rankpadel.dto.FranjaSedeDto;
import com.padel.rankpadel.enums.MedioPago;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un club del torneo, para el panel. Los montos y los pagos van en null para quien no es
 * el dueño: lo que se le paga a cada club es un número del negocio.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorneoSedeResponse {

    private Long id;
    private Long lugarId;
    private String lugarNombre;
    private String lugarDireccion;
    private boolean principal;
    private List<CanchaSede> canchas;
    private List<FranjaSedeDto> franjas;
    private String notas;

    private BigDecimal montoAcordado;
    private BigDecimal totalPagado;
    private BigDecimal saldo;
    private List<Pago> pagos;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CanchaSede {
        private Long id;
        private String nombre;
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
        private String registradoPor;
        private LocalDateTime creadoEn;
        private boolean anulado;
        private String anuladoPor;
        private String motivoAnulacion;
    }
}
