package com.padel.rankpadel.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;

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
public class TorneoResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private String imagenUrl;
    private Integer cupoMaximoParejas;
    private Map<Long, Integer> cuposPorCategoria;
    private FormatoTorneo formato;
    private EstadoTorneo estado;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private boolean esMixto;
    private boolean sumaPuntosRanking;
    private Long plantillaFormatoId;
    private String plantillaFormatoNombre;
    private Long plantillaPuntosId;
    private String plantillaPuntosNombre;
    private Integer cantidadParejasObjetivo;
    private Integer cantidadGrupos;
    private Integer parejasPorGrupo;
    private Integer avanzanPorGrupo;
    private boolean incluyeFaseGrupos;
    private boolean incluyeEliminacion;
    private TipoSorteo tipoSorteo;
    private Long temporadaId;
    private String temporadaNombre;
    private Long lugarId;
    private String lugarNombre;
    private List<CategoriaResponse> categorias;
    private long cantidadCategorias;
    private long cantidadParejas;
    private long cantidadPartidos;
    private long partidosFinalizados;
    private List<ConfiguracionPuntosResponse> configuracionPuntos;

}
