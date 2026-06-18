package com.padel.rankpadel.mapper;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.response.PartidoResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;

import jakarta.persistence.EntityNotFoundException;

@Component
public class PartidoMapper {

        public PartidoResponse partidoToResponse(Partido partido) {
                Categoria categoria = obtenerCategoria(partido);

                PartidoResponse.PartidoResponseBuilder builder = PartidoResponse.builder()
                                .id(partido.getId())
                                .estado(partido.getEstado())
                                .fase(partido.getFase())
                                .fechaHora(partido.getFechaHora())
                                .fechaHoraProgramada(partido.getFechaHoraProgramada())
                                .canchaId(partido.getCancha() != null ? partido.getCancha().getId() : null)
                                .canchaNombre(partido.getCancha() != null ? partido.getCancha().getNombre() : null)
                                .torneoId(partido.getTorneo() != null ? partido.getTorneo().getId() : null)
                                .torneoNombre(partido.getTorneo() != null ? partido.getTorneo().getNombre() : null)
                                .categoriaId(categoria != null ? categoria.getId() : null)
                                .categoriaNombre(categoria != null ? categoria.getNombre() : null)
                                .lugarId(partido.getTorneo() != null && partido.getTorneo().getLugar() != null
                                                ? partido.getTorneo().getLugar().getId()
                                                : null)
                                .lugarNombre(partido.getTorneo() != null && partido.getTorneo().getLugar() != null
                                                ? partido.getTorneo().getLugar().getNombre()
                                                : null)
                                .grupoId(partido.getGrupo() != null ? partido.getGrupo().getId() : null)
                                .grupoNombre(partido.getGrupo() != null ? partido.getGrupo().getNombre() : null)
                                .rondaId(partido.getRonda() != null ? partido.getRonda().getId() : null)
                                .ronda(partido.getRonda() != null ? partido.getRonda().getNombre() : null)
                                .rondaOrden(partido.getRonda() != null ? partido.getRonda().getOrden() : null)
                                .jornada(partido.getJornada())
                                .parejaLocalId(partido.getLocal() != null ? partido.getLocal().getId() : null)
                                .parejaVisitanteId(partido.getVisitante() != null ? partido.getVisitante().getId() : null)
                                .marcador(partido.getMarcador())
                                .ganadorId(partido.getGanador() != null ? partido.getGanador().getId() : null)
                                .ganadorNombre(formatearPareja(partido.getGanador()));

                if (partido.getLocal() != null) {
                        builder
                                        .jugadorLocal1Id(obtenerJugador1Id(partido.getLocal()))
                                        .jugadorLocal1Nombre(obtenerJugador1Nombre(partido.getLocal()))
                                        .jugadorLocal2Id(obtenerJugador2Id(partido.getLocal()))
                                        .jugadorLocal2Nombre(obtenerJugador2Nombre(partido.getLocal()));
                }

                if (partido.getVisitante() != null) {
                        builder
                                        .jugadorVisitante1Id(obtenerJugador1Id(partido.getVisitante()))
                                        .jugadorVisitante1Nombre(obtenerJugador1Nombre(partido.getVisitante()))
                                        .jugadorVisitante2Id(obtenerJugador2Id(partido.getVisitante()))
                                        .jugadorVisitante2Nombre(obtenerJugador2Nombre(partido.getVisitante()));
                }

                return builder.build();
        }

        private Categoria obtenerCategoria(Partido partido) {
                try {
                        if (partido.getLocal() != null && partido.getLocal().getCategoria() != null) {
                                return partido.getLocal().getCategoria();
                        }
                } catch (EntityNotFoundException e) {
                }

                try {
                        if (partido.getVisitante() != null && partido.getVisitante().getCategoria() != null) {
                                return partido.getVisitante().getCategoria();
                        }
                } catch (EntityNotFoundException e) {
                }

                if (partido.getGrupo() != null && partido.getGrupo().getCategoria() != null) {
                        return partido.getGrupo().getCategoria();
                }

                if (partido.getRonda() != null && partido.getRonda().getCategoria() != null) {
                        return partido.getRonda().getCategoria();
                }

                return null;
        }

        private String formatearPareja(Pareja pareja) {
                if (pareja == null) {
                        return null;
                }

                try {
                        return pareja.getJugador1().getNombre() + " " + pareja.getJugador1().getApellido()
                                        + " / "
                                        + pareja.getJugador2().getNombre() + " " + pareja.getJugador2().getApellido();
                } catch (EntityNotFoundException e) {
                        return null;
                }
        }

        private Long obtenerJugador1Id(Pareja pareja) {
                try {
                        return pareja.getJugador1() != null ? pareja.getJugador1().getId() : null;
                } catch (EntityNotFoundException e) {
                        return null;
                }
        }

        private String obtenerJugador1Nombre(Pareja pareja) {
                try {
                        return pareja.getJugador1() != null
                                        ? pareja.getJugador1().getNombre() + " " + pareja.getJugador1().getApellido()
                                        : null;
                } catch (EntityNotFoundException e) {
                        return null;
                }
        }

        private Long obtenerJugador2Id(Pareja pareja) {
                try {
                        return pareja.getJugador2() != null ? pareja.getJugador2().getId() : null;
                } catch (EntityNotFoundException e) {
                        return null;
                }
        }

        private String obtenerJugador2Nombre(Pareja pareja) {
                try {
                        return pareja.getJugador2() != null
                                        ? pareja.getJugador2().getNombre() + " " + pareja.getJugador2().getApellido()
                                        : null;
                } catch (EntityNotFoundException e) {
                        return null;
                }
        }

}
