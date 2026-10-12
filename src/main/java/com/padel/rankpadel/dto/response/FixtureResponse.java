package com.padel.rankpadel.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Los horarios del torneo para el panel, con lo que esté roto. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixtureResponse {

    private int descansoMinimoMinutos;
    private List<PartidoFixture> partidos;
    private List<Conflicto> conflictos;
    /** Partidos que se pueden jugar (las dos parejas definidas) y todavía no tienen horario. */
    private int sinProgramar;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartidoFixture {
        private Long id;
        private Long categoriaId;
        private String categoriaNombre;
        private FasePartido fase;
        /** "Zona A · fecha 2" o "Semifinales". */
        private String etapa;
        private EstadoPartido estado;
        private Long parejaLocalId;
        private Long parejaVisitanteId;
        private String local;
        private String visitante;
        private List<JugadorFixture> jugadores;
        private Long canchaId;
        private String canchaNombre;
        private Long lugarId;
        private String lugarNombre;
        private String lugarDireccion;
        private LocalDateTime inicio;
        private LocalDateTime fin;
        private int duracionMinutos;
        private String marcador;
        /** Si tiene las dos parejas y no se jugó: se le puede dar o cambiar horario. */
        private boolean programable;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JugadorFixture {
        private Long id;
        private String nombre;
        private String telefono;
        private Long parejaId;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Conflicto {
        private String tipo;
        private boolean grave;
        private List<Long> partidoIds;
        private String mensaje;
    }
}
