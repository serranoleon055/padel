package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.entity.CampeonTorneo;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RondaEliminatorias;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.repository.CampeonTorneoRepository;
import com.padel.rankpadel.repository.ConfiguracionCategoriaTorneoRepository;
import com.padel.rankpadel.repository.GrupoRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.PosicionGrupoRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampeonService - fecha de coronación")
class CampeonServiceTest {

    @Mock
    private CampeonTorneoRepository campeonTorneoRepository;
    @Mock
    private GrupoRepository grupoRepository;
    @Mock
    private PosicionGrupoRepository posicionGrupoRepository;
    @Mock
    private PartidoRepository partidoRepository;
    @Mock
    private TorneoRepository torneoRepository;
    @Mock
    private ConfiguracionCategoriaTorneoRepository configuracionCategoriaTorneoRepository;

    @InjectMocks
    private CampeonService campeonService;

    @Test
    @DisplayName("La fecha de coronación es la del torneo, no la del momento en que se tipeó el resultado")
    void recalcular_usaFechaDelTorneoYNoLaDeCargaDelResultado() {
        Categoria categoria = Categoria.builder().id(1L).nombre("3ra Masculino").build();
        Torneo torneo = Torneo.builder()
                .id(10L)
                .categorias(List.of(categoria))
                .incluyeEliminacion(true)
                .fechaInicio(LocalDate.of(2026, 1, 20))
                .fechaFin(LocalDate.of(2026, 1, 25))
                .build();

        RondaEliminatorias final_ = RondaEliminatorias.builder().nombre("Final").categoria(categoria).build();
        Pareja campeona = Pareja.builder().id(100L).build();
        Pareja subcampeona = Pareja.builder().id(200L).build();
        Partido finalPartido = Partido.builder()
                .fase(FasePartido.ELIMINACION)
                .estado(EstadoPartido.FINALIZADO)
                .ronda(final_)
                .local(campeona)
                .visitante(subcampeona)
                .ganador(campeona)
                .marcador("6-3 / 6-4")
                // Simula que el resultado se cargó hoy, meses después de jugado el
                // torneo: es justo el caso que rompía la fecha mostrada.
                .fechaHora(java.time.LocalDateTime.now())
                .build();

        when(configuracionCategoriaTorneoRepository.findByTorneoIdAndCategoriaId(10L, 1L))
                .thenReturn(java.util.Optional.<ConfiguracionCategoriaTorneo>empty());
        when(partidoRepository.findByTorneoId(10L)).thenReturn(List.of(finalPartido));

        campeonService.recalcularCampeones(torneo);

        ArgumentCaptor<List<CampeonTorneo>> capturado = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(campeonTorneoRepository).saveAll(capturado.capture());

        CampeonTorneo guardado = capturado.getValue().get(0);
        assertThat(guardado.getFechaCoronacion()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 25, 0, 0));
    }

    @Test
    @DisplayName("Sin fecha de fin cargada, usa la fecha de inicio del torneo")
    void recalcular_sinFechaFin_usaFechaInicio() {
        Categoria categoria = Categoria.builder().id(1L).nombre("3ra Masculino").build();
        Torneo torneo = Torneo.builder()
                .id(10L)
                .categorias(List.of(categoria))
                .incluyeEliminacion(true)
                .fechaInicio(LocalDate.of(2026, 3, 1))
                .fechaFin(null)
                .build();

        RondaEliminatorias final_ = RondaEliminatorias.builder().nombre("Final").categoria(categoria).build();
        Pareja campeona = Pareja.builder().id(100L).build();
        Partido finalPartido = Partido.builder()
                .fase(FasePartido.ELIMINACION)
                .estado(EstadoPartido.FINALIZADO)
                .ronda(final_)
                .local(campeona)
                .visitante(Pareja.builder().id(200L).build())
                .ganador(campeona)
                .marcador("6-0 / 6-0")
                .build();

        when(configuracionCategoriaTorneoRepository.findByTorneoIdAndCategoriaId(any(), any()))
                .thenReturn(java.util.Optional.<ConfiguracionCategoriaTorneo>empty());
        when(partidoRepository.findByTorneoId(10L)).thenReturn(List.of(finalPartido));

        campeonService.recalcularCampeones(torneo);

        ArgumentCaptor<List<CampeonTorneo>> capturado = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(campeonTorneoRepository).saveAll(capturado.capture());

        assertThat(capturado.getValue().get(0).getFechaCoronacion())
                .isEqualTo(java.time.LocalDateTime.of(2026, 3, 1, 0, 0));
    }
}
