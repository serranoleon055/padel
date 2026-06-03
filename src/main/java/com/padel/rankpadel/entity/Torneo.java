package com.padel.rankpadel.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "torneos")
public class Torneo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;
    private String imagenUrl;
    /** Cupo total (compatibilidad). El cupo efectivo se toma por categoría desde {@link #cuposPorCategoria}. */
    private Integer cupoMaximoParejas;

    /** Cupo máximo de parejas por categoría: categoriaId → cupo. */
    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "torneo_cupos_categoria", joinColumns = @JoinColumn(name = "torneo_id"))
    @jakarta.persistence.MapKeyColumn(name = "categoria_id")
    @Column(name = "cupo")
    private Map<Long, Integer> cuposPorCategoria = new HashMap<>();

    @Enumerated(EnumType.STRING)
    private FormatoTorneo formato;

    @Enumerated(EnumType.STRING)
    private EstadoTorneo estado;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private boolean esMixto;
    private boolean sumaPuntosRanking;
    @Builder.Default
    private boolean activo = true;
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

    @Enumerated(EnumType.STRING)
    private TipoSorteo tipoSorteo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lugar_id")
    private Lugar lugar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "temporada_id")
    private Temporada temporada;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "torneo_categorias", joinColumns = @JoinColumn(name = "torneo_id"), inverseJoinColumns = @JoinColumn(name = "categoria_id"))
    private List<Categoria> categorias = new ArrayList<>();

    @OneToMany(mappedBy = "torneo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConfiguracionPuntos> configuracionPuntos;

}
