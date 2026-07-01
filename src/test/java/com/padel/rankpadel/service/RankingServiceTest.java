package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.padel.rankpadel.dto.response.RankingResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionPuntos;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RankingEntry;
import com.padel.rankpadel.entity.Temporada;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;
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
    private Temporada temporadaActiva;
    private RankingEntry entry1;
    private RankingEntry entry2;

    @BeforeEach
    void setUp() {
        categoriaMasculina = Categoria.builder()
                .id(1L).nombre("Primera").genero(Genero.MASCULINO).build();

        temporadaActiva = Temporada.builder().id(7L).nombre("2026").activa(true).build();
        when(temporadaRepository.findFirstByActivaTrue()).thenReturn(Optional.of(temporadaActiva));

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
            when(rankingEntryRepository.findByTemporadaId(7L))
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
            when(rankingEntryRepository.findByCategoriaIdAndTemporadaId(1L, 7L))
                    .thenReturn(new ArrayList<>(List.of(entry1)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(1L, null);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getCategoriaId()).isEqualTo(1L);
            assertThat(resultado.get(0).getCategoriaNombre()).isEqualTo("Primera");
        }

        @Test
        @DisplayName("Con género filtra correctamente")
        void obtenerRanking_conGenero_filtraPorGenero() {
            when(rankingEntryRepository.findByCategoriaGeneroAndTemporadaId(Genero.MASCULINO, 7L))
                    .thenReturn(new ArrayList<>(List.of(entry1, entry2)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, Genero.MASCULINO);

            assertThat(resultado).hasSize(2);
        }

        @Test
        @DisplayName("Sin entries devuelve lista vacía")
        void obtenerRanking_sinEntries_devuelveListaVacia() {
            when(rankingEntryRepository.findByTemporadaId(7L))
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

            when(rankingEntryRepository.findByTemporadaId(7L))
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
            when(rankingEntryRepository.findByTemporadaId(7L))
                    .thenReturn(new ArrayList<>(List.of(entry1, entry2)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getPosicion()).isEqualTo(1);
            assertThat(resultado.get(1).getPosicion()).isEqualTo(2);
        }

        @Test
        @DisplayName("El nombre del jugador se construye concatenando nombre y apellido")
        void obtenerRanking_nombreJugador_concatenaNombreYApellido() {
            when(rankingEntryRepository.findByTemporadaId(7L))
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
            when(rankingEntryRepository.findByTemporadaId(7L))
                    .thenReturn(new ArrayList<>(List.of(entry1)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getTendencia()).isEqualTo("+1");
        }

        @Test
        @DisplayName("Jugador que bajó posiciones muestra tendencia negativa (-1)")
        void tendencia_jugadorQueBajo_muestraNegativo() {
            when(rankingEntryRepository.findByTemporadaId(7L))
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

            when(rankingEntryRepository.findByTemporadaId(7L))
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

            when(rankingEntryRepository.findByTemporadaId(7L))
                    .thenReturn(new ArrayList<>(List.of(entryEstable)));

            List<RankingResponse> resultado = rankingService.obtenerRanking(null, null);

            assertThat(resultado.get(0).getTendencia()).isEqualTo("-");
        }
    }

    @Nested
    @DisplayName("recalcularRankingCategoria() - blindaje determinista")
    class BlindajeRankingTests {

        private RankingEntry entradaCero(Jugador jugador) {
            return RankingEntry.builder()
                    .jugador(jugador).categoria(categoriaMasculina)
                    .puntosTotales(0).victorias(0).derrotas(0)
                    .posicionActual(0).posicionAnterior(0).build();
        }

        @Test
        @DisplayName("El total no depende del orden de carga (recálculo cronológico con piso 0)")
        void recalculo_independienteDelOrdenDeCarga() {
            Jugador x1 = Jugador.builder().id(11L).nombre("Ana").apellido("X").genero(Genero.MASCULINO).build();
            Jugador x2 = Jugador.builder().id(12L).nombre("Beto").apellido("X").genero(Genero.MASCULINO).build();
            Jugador o1 = Jugador.builder().id(21L).nombre("Caro").apellido("O").genero(Genero.MASCULINO).build();
            Jugador o2 = Jugador.builder().id(22L).nombre("Dani").apellido("O").genero(Genero.MASCULINO).build();

            Torneo torneo = Torneo.builder().id(10L).sumaPuntosRanking(true).build();

            Pareja parejaX = Pareja.builder().id(100L).categoria(categoriaMasculina).jugador1(x1).jugador2(x2).build();
            Pareja parejaO = Pareja.builder().id(200L).categoria(categoriaMasculina).jugador1(o1).jugador2(o2).build();

            Partido temprano = Partido.builder().id(1L).torneo(torneo).fase(FasePartido.GRUPOS)
                    .estado(EstadoPartido.FINALIZADO).fechaHora(LocalDateTime.now().minusHours(2))
                    .local(parejaX).visitante(parejaO).ganador(parejaO).build();
            Partido tardio = Partido.builder().id(2L).torneo(torneo).fase(FasePartido.GRUPOS)
                    .estado(EstadoPartido.FINALIZADO).fechaHora(LocalDateTime.now().minusHours(1))
                    .local(parejaX).visitante(parejaO).ganador(parejaX).build();

            ConfiguracionPuntos configGrupos = ConfiguracionPuntos.builder()
                    .nombreRonda("Grupos").puntosGanador(100).puntosPerdedor(-50).orden(1).torneo(torneo).build();

            RankingEntry eX1 = entradaCero(x1);
            RankingEntry eX2 = entradaCero(x2);
            RankingEntry eO1 = entradaCero(o1);
            RankingEntry eO2 = entradaCero(o2);

            when(partidoRepository.findPartidosQueSumanPuntos()).thenReturn(new ArrayList<>(List.of(temprano, tardio)));
            when(configuracionPuntosRepository.findByTorneoIdAndCategoriaIdOrderByOrden(10L, 1L)).thenReturn(List.of(configGrupos));
            when(rankingEntryRepository.findByCategoriaIdAndTemporadaId(1L, 7L))
                    .thenReturn(new ArrayList<>(List.of(eX1, eX2, eO1, eO2)));
            when(rankingEntryRepository.findByJugadorIdAndCategoriaIdAndTemporadaId(11L, 1L, 7L)).thenReturn(Optional.of(eX1));
            when(rankingEntryRepository.findByJugadorIdAndCategoriaIdAndTemporadaId(12L, 1L, 7L)).thenReturn(Optional.of(eX2));
            when(rankingEntryRepository.findByJugadorIdAndCategoriaIdAndTemporadaId(21L, 1L, 7L)).thenReturn(Optional.of(eO1));
            when(rankingEntryRepository.findByJugadorIdAndCategoriaIdAndTemporadaId(22L, 1L, 7L)).thenReturn(Optional.of(eO2));

            // Disparo el recálculo con el partido más nuevo (orden de carga "invertido")
            rankingService.actualizarRanking(tardio);

            // Cronológico con piso 0: X pierde -50 (piso 0) y luego gana +100 -> 100
            assertThat(eX1.getPuntosTotales()).isEqualTo(100);
            assertThat(eX2.getPuntosTotales()).isEqualTo(100);
            // O gana +100 y luego pierde -50 -> 50
            assertThat(eO1.getPuntosTotales()).isEqualTo(50);
            assertThat(eO2.getPuntosTotales()).isEqualTo(50);

            // Disparo de nuevo con el partido más viejo: mismo total
            rankingService.actualizarRanking(temprano);
            assertThat(eX1.getPuntosTotales()).isEqualTo(100);
            assertThat(eO1.getPuntosTotales()).isEqualTo(50);
        }
    }
}
