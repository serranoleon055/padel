package com.padel.rankpadel.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.ResultadoRequest;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RondaEliminatorias;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.PartidoMapper;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.PosicionGrupoRepository;
import com.padel.rankpadel.repository.RondaEliminatoriasRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResultadoService - Tests Unitarios")
class ResultadoServiceTest {

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private PosicionGrupoRepository posicionGrupoRepository;

    @Mock
    private PartidoMapper partidoMapper;

    @Mock
    private TorneoRepository torneoRepository;

    @Mock
    private RondaEliminatoriasRepository rondaEliminatoriasRepository;

    @Mock
    private RankingService rankingService;

    @InjectMocks
    private ResultadoService resultadoService;

    private Torneo torneo;
    private Pareja parejaLocal;
    private Pareja parejaVisitante;
    private RondaEliminatorias rondaTest;
    private Partido partidoPendiente;

    @BeforeEach
    void setUp() {
        torneo = Torneo.builder()
                .id(1L).nombre("Torneo Test")
                .estado(EstadoTorneo.EN_CURSO).sumaPuntosRanking(false)
                .build();

        parejaLocal = Pareja.builder().id(10L).build();
        parejaVisitante = Pareja.builder().id(20L).build();

        rondaTest = RondaEliminatorias.builder()
                .id(5L).nombre("Semifinal").orden(2)
                .torneo(torneo).build();

        partidoPendiente = Partido.builder()
                .id(100L).torneo(torneo)
                .local(parejaLocal).visitante(parejaVisitante)
                .estado(EstadoPartido.PENDIENTE)
                .fase(FasePartido.ELIMINACION)
                .ronda(rondaTest)
                .build();
    }

    @Nested
    @DisplayName("cargarResultado() - validaciones previas")
    class ValidacionesTests {

        @Test
        @DisplayName("Partido inexistente lanza ResourceNotFoundException")
        void cargarResultado_partidoNoExiste_lanzaExcepcion() {
            when(partidoRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> resultadoService.cargarResultado(1L, 999L,
                            new ResultadoRequest("6-3 / 6-4")));

            verify(partidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Partido que no pertenece al torneo lanza EstadoInvalidoException")
        void cargarResultado_partidoNoPerteneceTorneo_lanzaExcepcion() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.cargarResultado(99L, 100L,
                            new ResultadoRequest("6-3 / 6-4")));

            verify(partidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Partido ya FINALIZADO no puede recibir resultado nuevamente")
        void cargarResultado_partidoFinalizado_lanzaExcepcion() {
            Partido partidoFinalizado = Partido.builder()
                    .id(100L).torneo(torneo)
                    .local(parejaLocal).visitante(parejaVisitante)
                    .estado(EstadoPartido.FINALIZADO)
                    .fase(FasePartido.ELIMINACION).ronda(rondaTest)
                    .build();

            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoFinalizado));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.cargarResultado(1L, 100L,
                            new ResultadoRequest("6-3 / 6-4")));

            verify(partidoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("determinarGanador() - validaciones de marcador")
    class DeterminarGanadorTests {

        @Test
        @DisplayName("Marcador con formato inválido lanza EstadoInvalidoException")
        void determinarGanador_marcadorInvalido_lanzaExcepcion() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.cargarResultado(1L, 100L,
                            new ResultadoRequest("seis-tres")));

            verify(partidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Set empatado lanza EstadoInvalidoException")
        void determinarGanador_setEmpatado_lanzaExcepcion() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.cargarResultado(1L, 100L,
                            new ResultadoRequest("6-6")));

            verify(partidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Marcador sin ganador claro lanza EstadoInvalidoException")
        void determinarGanador_marcadorSinGanador_lanzaExcepcion() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.cargarResultado(1L, 100L,
                            new ResultadoRequest("6-3 / 3-6")));

            verify(partidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Marcador válido 2 sets: el partido se guarda con ganador asignado")
        void determinarGanador_marcadorValido2Sets_guardaPartido() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));
            when(partidoMapper.partidoToResponse(any())).thenReturn(
                    com.padel.rankpadel.dto.response.PartidoResponse.builder()
                            .id(100L).marcador("6-3 / 6-4").build());

            resultadoService.cargarResultado(1L, 100L, new ResultadoRequest("6-3 / 6-4"));

            verify(partidoRepository).save(any(Partido.class));
        }

        @Test
        @DisplayName("Marcador válido 3 sets: el partido se guarda con ganador asignado")
        void determinarGanador_marcadorValido3Sets_guardaPartido() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));
            when(partidoMapper.partidoToResponse(any())).thenReturn(
                    com.padel.rankpadel.dto.response.PartidoResponse.builder()
                            .id(100L).marcador("6-3 / 3-6 / 7-5").build());

            resultadoService.cargarResultado(1L, 100L, new ResultadoRequest("6-3 / 3-6 / 7-5"));

            verify(partidoRepository).save(any(Partido.class));
        }

        @Test
        @DisplayName("Marcador de un solo set no es un partido completo")
        void determinarGanador_unSoloSet_lanzaExcepcion() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.cargarResultado(1L, 100L,
                            new ResultadoRequest("6-3")));

            verify(partidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("No puede haber un tercer set si el partido ya estaba 2-0")
        void determinarGanador_tercerSetInnecesario_lanzaExcepcion() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.cargarResultado(1L, 100L,
                            new ResultadoRequest("6-1 / 6-2 / 6-3")));

            verify(partidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Partido con ronda que cierra bracket finaliza el torneo")
        void cargarResultado_ultimoPartidoRonda_finalizaTorneo() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));
            when(partidoRepository.findByRondaId(5L))
                    .thenReturn(List.of(partidoPendiente));
            when(partidoRepository.findByRondaIdOrderByOrdenLlaveAscIdAsc(5L))
                    .thenReturn(List.of(partidoPendiente));
            when(partidoMapper.partidoToResponse(any())).thenReturn(
                    com.padel.rankpadel.dto.response.PartidoResponse.builder().id(100L).build());

            resultadoService.cargarResultado(1L, 100L, new ResultadoRequest("6-3 / 6-4"));

            verify(torneoRepository).save(any(Torneo.class));
        }

        @Test
        @DisplayName("Final de una categoría NO finaliza el torneo si otra categoría sigue con partidos pendientes")
        void cargarResultado_finalDeCategoria_noFinalizaSiQuedanOtrasCategorias() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));
            when(partidoRepository.findByRondaId(5L)).thenReturn(List.of(partidoPendiente));
            when(partidoRepository.findByRondaIdOrderByOrdenLlaveAscIdAsc(5L))
                    .thenReturn(List.of(partidoPendiente));
            // Otra categoría del mismo torneo todavía tiene un partido (de grupos) sin jugar.
            Partido pendienteOtraCategoria = Partido.builder()
                    .id(200L).torneo(torneo)
                    .estado(EstadoPartido.PENDIENTE).fase(FasePartido.GRUPOS)
                    .build();
            when(partidoRepository.findByTorneoId(1L))
                    .thenReturn(List.of(partidoPendiente, pendienteOtraCategoria));
            when(partidoMapper.partidoToResponse(any())).thenReturn(
                    com.padel.rankpadel.dto.response.PartidoResponse.builder().id(100L).build());

            resultadoService.cargarResultado(1L, 100L, new ResultadoRequest("6-3 / 6-4"));

            // El torneo NO debe finalizarse ni cerrarse el ranking todavía.
            verify(torneoRepository, never()).save(any(Torneo.class));
            verify(rankingService, never()).cerrarTorneo(any());
        }
    }

    @Nested
    @DisplayName("determinarGanador() - torneo a 1 set")
    class FormatoUnSetTests {

        private Partido partidoUnSet() {
            Torneo torneoUnSet = Torneo.builder()
                    .id(1L).nombre("Minitorneo")
                    .estado(EstadoTorneo.EN_CURSO).sumaPuntosRanking(false)
                    .mejorDeSets(1)
                    .build();
            return Partido.builder()
                    .id(100L).torneo(torneoUnSet)
                    .local(parejaLocal).visitante(parejaVisitante)
                    .estado(EstadoPartido.PENDIENTE)
                    .fase(FasePartido.ELIMINACION).ronda(rondaTest)
                    .build();
        }

        @Test
        @DisplayName("Un único set define el partido")
        void determinarGanador_unSet_guardaPartido() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoUnSet()));
            when(partidoMapper.partidoToResponse(any())).thenReturn(
                    com.padel.rankpadel.dto.response.PartidoResponse.builder().id(100L).marcador("6-3").build());

            resultadoService.cargarResultado(1L, 100L, new ResultadoRequest("6-3"));

            verify(partidoRepository).save(any(Partido.class));
        }

        @Test
        @DisplayName("Cargar 2 sets en un torneo a 1 set es inválido")
        void determinarGanador_dosSetsEnTorneoUnSet_lanzaExcepcion() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoUnSet()));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.cargarResultado(1L, 100L, new ResultadoRequest("6-3 / 6-4")));

            verify(partidoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("corregirResultado() - guardas de seguridad")
    class CorregirResultadoTests {

        @Test
        @DisplayName("Un partido no FINALIZADO no se puede corregir")
        void corregir_partidoNoFinalizado_lanzaExcepcion() {
            when(partidoRepository.findById(100L)).thenReturn(Optional.of(partidoPendiente));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.corregirResultado(1L, 100L, new ResultadoRequest("6-3 / 6-4")));

            verify(partidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Si ya se generó la ronda siguiente, no permite corregir")
        void corregir_conRondaPosterior_lanzaExcepcion() {
            Categoria categoria = Categoria.builder().id(3L).build();
            RondaEliminatorias rondaActual = RondaEliminatorias.builder()
                    .id(5L).orden(1).torneo(torneo).categoria(categoria).build();
            Partido finalizado = Partido.builder()
                    .id(100L).torneo(torneo)
                    .local(parejaLocal).visitante(parejaVisitante)
                    .estado(EstadoPartido.FINALIZADO).fase(FasePartido.ELIMINACION)
                    .ronda(rondaActual).marcador("6-3 / 6-4").ganador(parejaLocal)
                    .build();
            RondaEliminatorias rondaPosterior = RondaEliminatorias.builder()
                    .id(6L).orden(2).categoria(categoria).build();

            when(partidoRepository.findById(100L)).thenReturn(Optional.of(finalizado));
            when(rondaEliminatoriasRepository.findByTorneoIdAndCategoriaIdOrderByOrden(1L, 3L))
                    .thenReturn(List.of(rondaActual, rondaPosterior));

            assertThrows(EstadoInvalidoException.class,
                    () -> resultadoService.corregirResultado(1L, 100L, new ResultadoRequest("6-4 / 6-2")));

            verify(partidoRepository, never()).save(any());
        }
    }
}
