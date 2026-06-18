package com.padel.rankpadel.dto.response;

import java.time.LocalDateTime;

import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;

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
public class PartidoResponse {

    private Long id;
    private EstadoPartido estado;
    private FasePartido fase;
    private LocalDateTime fechaHora;
    private LocalDateTime fechaHoraProgramada;
    private Long canchaId;
    private String canchaNombre;
    private Long torneoId;
    private String torneoNombre;
    private Long categoriaId;
    private String categoriaNombre;
    private Long lugarId;
    private String lugarNombre;
    private Long grupoId;
    private String grupoNombre;
    private Long rondaId;
    private String ronda;
    private Integer rondaOrden;
    private Integer jornada;
    private Long parejaLocalId;
    private Long parejaVisitanteId;
    private String marcador;
    private Long jugadorLocal1Id;
    private String jugadorLocal1Nombre;
    private Long jugadorLocal2Id;
    private String jugadorLocal2Nombre;
    private Long jugadorVisitante1Id;
    private String jugadorVisitante1Nombre;
    private Long jugadorVisitante2Id;
    private String jugadorVisitante2Nombre;
    private Long ganadorId;
    private String ganadorNombre;

}
