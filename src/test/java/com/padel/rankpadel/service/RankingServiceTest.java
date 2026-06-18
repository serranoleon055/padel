package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.response.RankingResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.RankingEntry;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.repository.ConfiguracionPuntosRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.RankingEntryRepository;
import com.padel.rankpadel.repository.TemporadaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingService - Tests Unitarios")
class RankingServiceTest {

    @Mock
    private RankingEntryRepository rankingEntryRepository;

    @Mock
    private ConfiguracionPuntosRepository configuracionPuntosRepository;

    @Mock
    private ParejaRepository parejaRepository;

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private TemporadaRepository temporadaRepository;

    @InjectMocks
    private RankingService rankingService;

    private Jugador jugador1;
    private Jugador jugador2;
    private Categoria categoriaMasculina;
    private RankingEntry entry1;
    private RankingEntry entry2;

    @BeforeEach
    void setUp() {
        categoriaMasculina = Categoria.builder()
                .id(1L).nombre("Primera").genero(Genero.MASCULINO).build();

        jugador1 = Jugador.builder()
                .id(1L).nombre("Carlos").apellido("García").genero(Genero.MASCULINO).build();

        jugador2 = Jugador.builder()
                .id(2L).nombre("Pedro").apellido("López").genero(Genero.MASCULINO).build();

        entry1 = RankingEntry.builder()
                .id(1L).jugador(jugador1).categoria(categoriaMasculina)
                .puntosTotales(300).torneosJugados(3).victorias(8).derrotas(2)
                .posicionActual(1).posicionAnterior(2).build();

        entry2 = RankingEntry.builder()
                .id(2L).jugador(jugador2).categoria(categoriaMasculina)
                .puntosTotales(150).torneosJugados(2).victorias(4).derrotas(3)
                .posicionActual(2).posicionAnterior(1).build();
    }

    @Nested
    @DisplayName("obtenerRanking() - filtros")
    class ObtenerRankingFiltrosTests {

        @Test
        @DisplayName("Sin filtros devuelve todos los entries ordenados por puntos descendente")
        void obtenerRanking_sinFiltros_devuelveTodosOrdenados() {
            // entry2 primero (150 pts) para verificar que el servicio los reordena
            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>(List.of(entry2, entry1)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getPuntosTotales()).isEqualTo(300);
            assertThat(resultado.get(0).getJugadorNombre()).isEqualTo("Carlos García");
            assertThat(resultado.get(1).getPuntosTotales()).isEqualTo(150);
        }

        @Test
        @DisplayName("Con categoriaId filtra correctamente")
        void obtenerRanking_conCategoriaId_filtraPorCategoria() {
            when(rankingEntryRepository.findByCategoriaIdAndTemporadaIsNull(1L))
                    .thenReturn(new ArrayList<>(List.of(entry1)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(1L, null);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getCategoriaId()).isEqualTo(1L);
            assertThat(resultado.get(0).getCategoriaNombre()).isEqualTo("Primera");
        }

        @Test
        @DisplayName("Con género filtra correctamente")
        void obtenerRanking_conGenero_filtraPorGenero() {
            when(rankingEntryRepository.findByCategoriaGeneroAndTemporadaIsNull(Genero.MASCULINO))
                    .thenReturn(new ArrayList<>(List.of(entry1, entry2)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, Genero.MASCULINO);

            assertThat(resultado).hasSize(2);
        }

        @Test
        @DisplayName("Sin entries devuelve lista vacía")
        void obtenerRanking_sinEntries_devuelveListaVacia() {
            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>());

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("Empate en puntos: ordena por más victorias primero (desempate determinístico)")
        void obtenerRanking_empatePuntos_ordenaPorVictorias() {
            RankingEntry menosVictorias = RankingEntry.builder()
                    .id(1L).jugador(jugador1).categoria(categoriaMasculina)
                    .puntosTotales(200).victorias(3).derrotas(5)
                    .posicionActual(1).posicionAnterior(1).build();
            RankingEntry masVictorias = RankingEntry.builder()
                    .id(2L).jugador(jugador2).categoria(categoriaMasculina)
                    .puntosTotales(200).victorias(7).derrotas(1)
                    .posicionActual(2).posicionAnterior(2).build();

            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>(List.of(menosVictorias, masVictorias)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getJugadorNombre()).isEqualTo("Pedro López");
            assertThat(resultado.get(1).getJugadorNombre()).isEqualTo("Carlos García");
        }
    }

    @Nested
    @DisplayName("obtenerRanking() - mapeo de posiciones")
    class ObtenerRankingPosicionesTests {

        @Test
        @DisplayName("Las posiciones en la respuesta son 1-based y correlativas")
        void obtenerRanking_posiciones_son1BasedYCorrelativas() {
            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>(List.of(entry1, entry2)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getPosicion()).isEqualTo(1);
            assertThat(resultado.get(1).getPosicion()).isEqualTo(2);
        }

        @Test
        @DisplayName("El nombre del jugador se construye concatenando nombre y apellido")
        void obtenerRanking_nombreJugador_concatenaNombreYApellido() {
            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>(List.of(entry1)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getJugadorNombre()).isEqualTo("Carlos García");
        }
    }

    @Nested
    @DisplayName("obtenerRanking() - tendencia")
    class TendenciaTests {

        @Test
        @DisplayName("Jugador que subió posiciones muestra tendencia positiva (+1)")
        void tendencia_jugadorQueSubio_muestraPositivo() {
            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>(List.of(entry1)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getTendencia()).isEqualTo("+1");
        }

        @Test
        @DisplayName("Jugador que bajó posiciones muestra tendencia negativa (-1)")
        void tendencia_jugadorQueBajo_muestraNegativo() {
            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>(List.of(entry2)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getTendencia()).isEqualTo("-1");
        }

        @Test
        @DisplayName("Jugador nuevo (posicionAnterior=0) muestra guión")
        void tendencia_jugadorNuevo_muestraGuion() {
            RankingEntry entryNuevo = RankingEntry.builder()
                    .id(3L).jugador(jugador1).categoria(categoriaMasculina)
                    .puntosTotales(100).posicionActual(1).posicionAnterior(0).build();

            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>(List.of(entryNuevo)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getTendencia()).isEqualTo("-");
        }

        @Test
        @DisplayName("Jugador que mantuvo posición muestra guión")
        void tendencia_jugadorQueMantuvo_muestraGuion() {
            RankingEntry entryEstable = RankingEntry.builder()
                    .id(4L).jugador(jugador1).categoria(categoriaMasculina)
                    .puntosTotales(200).posicionActual(3).posicionAnterior(3).build();

            when(rankingEntryRepository.findByTemporadaIsNull())
                    .thenReturn(new ArrayList<>(List.of(entryEstable)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getTendencia()).isEqualTo("-");
        }
    }
}
