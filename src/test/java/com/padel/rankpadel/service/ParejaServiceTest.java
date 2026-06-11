package com.padel.rankpadel.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.ParejaRequest;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.mapper.ParejaMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParejaService - inscripción y multi-categoría")
class ParejaServiceTest {

    @Mock
    private ParejaRepository parejaRepository;
    @Mock
    private ParejaMapper parejaMapper;
    @Mock
    private TorneoRepository torneoRepository;
    @Mock
    private JugadorRepository jugadorRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private PartidoRepository partidoRepository;
    @Mock
    private ResultadoService resultadoService;

    @InjectMocks
    private ParejaService parejaService;

    private Categoria categoria;
    private Torneo torneo;
    private Jugador jugador1;
    private Jugador jugador2;

    @BeforeEach
    void setUp() {
        categoria = Categoria.builder().id(7L).nombre("Cuarta").genero(Genero.MASCULINO).build();
        torneo = Torneo.builder()
                .id(1L).estado(EstadoTorneo.INSCRIPCION).esMixto(false)
                .categorias(List.of(categoria))
                .build();
        jugador1 = Jugador.builder().id(10L).nombre("Juan").apellido("Perez").genero(Genero.MASCULINO).build();
        jugador2 = Jugador.builder().id(20L).nombre("Pedro").apellido("Gomez").genero(Genero.MASCULINO).build();
    }

    private ParejaRequest request() {
        return ParejaRequest.builder().categoriaId(7L).jugador1Id(10L).jugador2Id(20L).build();
    }

    private void mockInscripcionValida() {
        when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneo));
        when(categoriaRepository.findById(7L)).thenReturn(Optional.of(categoria));
        when(jugadorRepository.findById(10L)).thenReturn(Optional.of(jugador1));
        when(jugadorRepository.findById(20L)).thenReturn(Optional.of(jugador2));
    }

    @Test
    @DisplayName("Rechaza al jugador ya inscripto en la MISMA categoría")
    void rechazaDuplicadoEnMismaCategoria() {
        mockInscripcionValida();
        when(parejaRepository.jugadorYaInscriptoEnCategoria(1L, 7L, 10L)).thenReturn(true);

        assertThrows(EstadoInvalidoException.class,
                () -> parejaService.inscribir(1L, request()));

        verify(parejaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Permite la inscripción cuando los jugadores no están en esa categoría (multi-categoría)")
    void permiteInscripcionEnOtraCategoria() {
        mockInscripcionValida();
        when(parejaRepository.jugadorYaInscriptoEnCategoria(eq(1L), eq(7L), anyLong())).thenReturn(false);
        when(parejaMapper.requestToPareja(any(), any(), any(), any(), any()))
                .thenReturn(Pareja.builder().id(99L).build());

        parejaService.inscribir(1L, request());

        verify(parejaRepository).save(any(Pareja.class));
    }
}
