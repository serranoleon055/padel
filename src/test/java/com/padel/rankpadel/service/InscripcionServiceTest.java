package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.IntegranteInscripcionRequest;
import com.padel.rankpadel.dto.request.ParejaRequest;
import com.padel.rankpadel.dto.request.SolicitudInscripcionRequest;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoSolicitud;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.SolicitudInscripcionRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("InscripcionService - solicitudes")
class InscripcionServiceTest {

    @Mock
    private SolicitudInscripcionRepository solicitudInscripcionRepository;
    @Mock
    private TorneoRepository torneoRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private JugadorRepository jugadorRepository;
    @Mock
    private ParejaService parejaService;

    @org.mockito.Mock
    private CobroInscripcionService cobroInscripcionService;

    @org.mockito.Mock
    private com.padel.rankpadel.repository.ParejaRepository parejaRepository;

    @InjectMocks
    private InscripcionService inscripcionService;

    @Test
    @DisplayName("No permite inscribirse si el torneo no está en INSCRIPCION")
    void crear_torneoNoEnInscripcion_lanza() {
        Torneo torneo = Torneo.builder().id(1L).estado(EstadoTorneo.SORTEADO).build();
        when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneo));

        SolicitudInscripcionRequest request = SolicitudInscripcionRequest.builder()
                .categoriaId(7L)
                .jugador1(IntegranteInscripcionRequest.builder().jugadorId(10L).build())
                .jugador2(IntegranteInscripcionRequest.builder().jugadorId(20L).build())
                .build();

        assertThrows(EstadoInvalidoException.class, () -> inscripcionService.crear(1L, request));
        verify(solicitudInscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aprobar reutiliza la inscripción de pareja y marca la solicitud APROBADA")
    void aprobar_conJugadoresExistentes_creaParejaYMarcaAprobada() {
        Torneo torneo = Torneo.builder().id(1L).estado(EstadoTorneo.INSCRIPCION).build();
        Categoria categoria = Categoria.builder().id(7L).build();
        Jugador jugador1 = Jugador.builder().id(10L).nombre("Juan").apellido("Perez").build();
        Jugador jugador2 = Jugador.builder().id(20L).nombre("Pedro").apellido("Gomez").build();
        SolicitudInscripcion solicitud = SolicitudInscripcion.builder()
                .id(5L).torneo(torneo).categoria(categoria).estado(EstadoSolicitud.PENDIENTE)
                .jugador1(jugador1).jugador2(jugador2).build();

        solicitud.setCostoAplicado(new java.math.BigDecimal("15000"));
        com.padel.rankpadel.entity.Pareja pareja = com.padel.rankpadel.entity.Pareja.builder().id(30L).build();

        when(solicitudInscripcionRepository.findById(5L)).thenReturn(Optional.of(solicitud));
        when(parejaService.inscribir(eq(1L), any(ParejaRequest.class)))
                .thenReturn(com.padel.rankpadel.dto.response.ParejaResponse.builder().id(30L).build());
        when(parejaRepository.findById(30L)).thenReturn(Optional.of(pareja));

        inscripcionService.aprobar(5L, null);

        verify(parejaService).inscribir(eq(1L), any(ParejaRequest.class));
        verify(solicitudInscripcionRepository).save(solicitud);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
        // La pareja queda atada a la solicitud y debe lo que se le dijo al anotarse.
        assertThat(solicitud.getPareja()).isSameAs(pareja);
        assertThat(pareja.getCostoInscripcionJugador()).isEqualByComparingTo("15000");
        verify(cobroInscripcionService).registrarSeniaOnline(solicitud);
    }
}
