package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class TorneoRequest {

    @NotBlank
    private String nombre;

    private String descripcion;
    private String imagenUrl;
    private Integer cupoMaximoParejas;

    private BigDecimal costoInscripcionJugador;
    private BigDecimal premioAcumulado;
    private Integer seniaPorcentaje;

    @Builder.Default
    private Map<Long, Integer> cuposPorCategoria = new HashMap<>();

    @NotNull
    private FormatoTorneo formato;

    @NotNull
    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private boolean sumaPuntosRanking;

    private Long plantillaFormatoId;

    private Long plantillaPuntosId;

    private Integer cantidadParejasObjetivo;

    private Integer cantidadGrupos;

    private Integer parejasPorGrupo;

    private Integer avanzanPorGrupo;

    private boolean incluyeFaseGrupos;

    private boolean incluyeEliminacion;

    @Builder.Default
    private Integer mejorDeSets = 3;

    @NotNull
    private TipoSorteo tipoSorteo;

    private Long temporadaId;
    private Long lugarId;

    @Builder.Default
    private List<Long> categoriaIds = new ArrayList<>();

    @Builder.Default
    private List<ConfiguracionPuntosRequest> configuracionPuntos = new ArrayList<>();

}
