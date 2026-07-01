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
public class SolicitudInscripcionResponse {

    private Long id;
    private Long torneoId;
    private String torneoNombre;
    private Long categoriaId;
    private String categoriaNombre;
    private String estado;
    private String telefonoContacto;
    private String jugador1;
    private String jugador2;
    private boolean jugador1EsNuevo;
    private boolean jugador2EsNuevo;
    private List<JugadorCandidatoResponse> jugador1Candidatos;
    private List<JugadorCandidatoResponse> jugador2Candidatos;
    private boolean pagada;
    private String estadoPago;
    private BigDecimal montoSenia;
}
