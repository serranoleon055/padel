package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.entity.Grupo;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.GrupoRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.PosicionGrupoRepository;
import com.padel.rankpadel.repository.RondaEliminatoriasRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("SorteoService - Tests Unitarios")
class SorteoServiceTest {

    @Mock
    private TorneoRepository torneoRepository;

    @Mock
    private ParejaRepository parejaRepository;

    @Mock
    private GrupoRepository grupoRepository;

    @Mock
    private PosicionGrupoRepository posicionGrupoRepository;

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private RondaEliminatoriasRepository rondaEliminatoriasRepository;

    @InjectMocks
    private SorteoService sorteoService;

    @Nested
    @DisplayName("generarSorteo() - precondiciones")
    class PrecondicionesTests {

        @Test
        @DisplayName("Torneo inexistente lanza ResourceNotFoundException")
        void generarSorteo_torneoNoExiste_lanzaExcepcion() {
            when(torneoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> sorteoService.generarSorteo(99L));

            verify(parejaRepository, never()).findByTorneoIdAndCategoriaId(any(), any());
            verify(partidoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Torneo en BORRADOR (no INSCRIPCION) lanza EstadoInvalidoException")
        void generarSorteo_torneoEnBorrador_lanzaExcepcion() {
            Torneo torneoBorrador = Torneo.builder()
                    .id(1L)
                    .estado(EstadoTorneo.BORRADOR)
                    .build();

            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBorrador));

            assertThrows(EstadoInvalidoException.class,
                    () -> sorteoService.generarSorteo(1L));

            verify(partidoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Torneo en EN_CURSO (no INSCRIPCION) lanza EstadoInvalidoException")
        void generarSorteo_torneoEnCurso_lanzaExcepcion() {
            Torneo torneoEnCurso = Torneo.builder()
                    .id(1L)
                    .estado(EstadoTorneo.EN_CURSO)
                    .build();

            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoEnCurso));

            assertThrows(EstadoInvalidoException.class,
                    () -> sorteoService.generarSorteo(1L));

            verify(partidoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Torneo FINALIZADO no puede sortearse nuevamente")
        void generarSorteo_torneoFinalizado_lanzaExcepcion() {
            Torneo torneoFinalizado = Torneo.builder()
                    .id(1L)
                    .estado(EstadoTorneo.FINALIZADO)
                    .build();

            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoFinalizado));

            assertThrows(EstadoInvalidoException.class,
                    () -> sorteoService.generarSorteo(1L));

            verify(partidoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Torneo CANCELADO no puede sortearse")
        void generarSorteo_torneoCancelado_lanzaExcepcion() {
            Torneo torneoCancelado = Torneo.builder()
                    .id(1L)
                    .estado(EstadoTorneo.CANCELADO)
                    .build();

            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoCancelado));

            assertThrows(EstadoInvalidoException.class,
                    () -> sorteoService.generarSorteo(1L));

            verify(partidoRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("Deshacer el sorteo")
    class DeshacerSorteo {

        private Torneo sorteado() {
            return Torneo.builder().id(1L).estado(EstadoTorneo.SORTEADO).build();
        }

        @Test
        @DisplayName("Borra el cuadro, limpia los cabezas de serie y vuelve a INSCRIPCION")
        void deshacer_borraElCuadro() {
            Torneo torneo = sorteado();
            Partido partido = Partido.builder().id(9L).estado(EstadoPartido.PENDIENTE).build();
            Grupo grupo = Grupo.builder().id(3L).build();
            Pareja pareja = Pareja.builder().id(4L).esCabezaDeSerie(true).build();
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneo));
            when(partidoRepository.findByTorneoId(1L)).thenReturn(List.of(partido));
            when(grupoRepository.findByTorneoId(1L)).thenReturn(List.of(grupo));
            when(posicionGrupoRepository.findByGrupoId(3L)).thenReturn(List.of());
            when(rondaEliminatoriasRepository.findByTorneoIdOrderByOrden(1L)).thenReturn(List.of());
            when(parejaRepository.findByTorneoId(1L)).thenReturn(List.of(pareja));

            sorteoService.deshacerSorteo(1L);

            verify(partidoRepository).deleteAll(List.of(partido));
            verify(grupoRepository).deleteAll(List.of(grupo));
            assertThat(pareja.isEsCabezaDeSerie()).isFalse();
            assertThat(torneo.getEstado()).isEqualTo(EstadoTorneo.INSCRIPCION);
        }

        /**
         * El freno que importa: con un resultado cargado, deshacer el sorteo borraría
         * partidos jugados. Para eso está reabrir el torneo, que es otra cosa.
         */
        @Test
        @DisplayName("Con un resultado ya cargado se rechaza y no borra nada")
        void deshacer_conResultados_rechaza() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(sorteado()));
            when(partidoRepository.findByTorneoId(1L)).thenReturn(List.of(
                    Partido.builder().id(9L).estado(EstadoPartido.PENDIENTE).build(),
                    Partido.builder().id(10L).estado(EstadoPartido.FINALIZADO).build()));

            assertThrows(EstadoInvalidoException.class, () -> sorteoService.deshacerSorteo(1L));

            verify(partidoRepository, never()).deleteAll(any());
            verify(grupoRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("Un torneo EN_CURSO no se deshace: ya arrancó")
        void deshacer_enCurso_rechaza() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(
                    Torneo.builder().id(1L).estado(EstadoTorneo.EN_CURSO).build()));

            assertThrows(EstadoInvalidoException.class, () -> sorteoService.deshacerSorteo(1L));

            verify(partidoRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("Un torneo que ni siquiera se sorteó tampoco")
        void deshacer_enInscripcion_rechaza() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(
                    Torneo.builder().id(1L).estado(EstadoTorneo.INSCRIPCION).build()));

            assertThrows(EstadoInvalidoException.class, () -> sorteoService.deshacerSorteo(1L));
        }
    }
}
