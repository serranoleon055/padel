package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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

import com.padel.rankpadel.dto.request.TorneoRequest;
import com.padel.rankpadel.dto.request.ConfiguracionPuntosRequest;
import com.padel.rankpadel.dto.response.TorneoResponse;
import com.padel.rankpadel.entity.ConfiguracionPuntos;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.entity.PlantillaFormato;
import com.padel.rankpadel.entity.PlantillaPuntos;
import com.padel.rankpadel.entity.PlantillaPuntosRonda;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.TorneoMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.ConfiguracionPuntosRepository;
import com.padel.rankpadel.repository.GrupoRepository;
import com.padel.rankpadel.repository.LugarRepository;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.PlantillaFormatoRepository;
import com.padel.rankpadel.repository.PlantillaPuntosRepository;
import com.padel.rankpadel.repository.PosicionGrupoRepository;
import com.padel.rankpadel.repository.TemporadaRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TorneoService - Tests Unitarios")
class TorneoServiceTest {

    @Mock
    private TorneoRepository torneoRepository;

    @Mock
    private TorneoMapper torneoMapper;

    @Mock
    private TemporadaRepository temporadaRepository;

    @Mock
    private LugarRepository lugarRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ConfiguracionPuntosRepository configuracionPuntosRepository;

    @Mock
    private PlantillaFormatoRepository plantillaFormatoRepository;

    @Mock
    private PlantillaPuntosRepository plantillaPuntosRepository;

    @Mock
    private ParejaRepository parejaRepository;

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private GrupoRepository grupoRepository;

    @Mock
    private PosicionGrupoRepository posicionGrupoRepository;

    @Mock
    private RankingService rankingService;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private TorneoService torneoService;

    private Torneo torneoBase;
    private TorneoResponse torneoResponseBase;

    @BeforeEach
    void setUp() {
        lenient().when(parejaRepository.countByTorneoId(any())).thenReturn(0L);
        lenient().when(partidoRepository.countByTorneoId(any())).thenReturn(0L);
        lenient().when(partidoRepository.countByTorneoIdAndEstado(any(), any())).thenReturn(0L);

        torneoBase = Torneo.builder()
                .id(1L)
                .nombre("Torneo Verano 2025")
                .formato(FormatoTorneo.ELIMINACION_DIRECTA)
                .estado(EstadoTorneo.BORRADOR)
                .fechaInicio(LocalDate.of(2025, 12, 1))
                .tipoSorteo(TipoSorteo.ALEATORIO)
                .build();

        torneoResponseBase = TorneoResponse.builder()
                .id(1L)
                .nombre("Torneo Verano 2025")
                .formato(FormatoTorneo.ELIMINACION_DIRECTA)
                .estado(EstadoTorneo.BORRADOR)
                .fechaInicio(LocalDate.of(2025, 12, 1))
                .tipoSorteo(TipoSorteo.ALEATORIO)
                .build();
    }

    @Nested
    @DisplayName("cambiarEstado()")
    class CambiarEstadoTests {

        @Test
        @DisplayName("BORRADOR → INSCRIPCION: transición válida guarda el nuevo estado")
        void cambiarEstado_deBorradorAInscripcion_exitoso() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));
            TorneoResponse responseEsperado = TorneoResponse.builder()
                    .id(1L)
                    .estado(EstadoTorneo.INSCRIPCION)
                    .build();
            when(torneoMapper.torneoToResponse(torneoBase)).thenReturn(responseEsperado);

            TorneoResponse resultado = torneoService.cambiarEstado(1L, EstadoTorneo.INSCRIPCION);

            assertThat(resultado.getEstado()).isEqualTo(EstadoTorneo.INSCRIPCION);
            assertThat(torneoBase.getEstado()).isEqualTo(EstadoTorneo.INSCRIPCION);

            verify(torneoRepository).save(torneoBase);
        }

        @Test
        @DisplayName("BORRADOR → CANCELADO: transición válida")
        void cambiarEstado_deBorradorACancelado_exitoso() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));
            when(torneoMapper.torneoToResponse(any(Torneo.class))).thenReturn(
                    TorneoResponse.builder().estado(EstadoTorneo.CANCELADO).build());

            torneoService.cambiarEstado(1L, EstadoTorneo.CANCELADO);

            assertThat(torneoBase.getEstado()).isEqualTo(EstadoTorneo.CANCELADO);
            verify(torneoRepository).save(torneoBase);
        }

        @Test
        @DisplayName("BORRADOR → FINALIZADO: transición inválida lanza EstadoInvalidoException")
        void cambiarEstado_deBorradorAFinalizado_lanzaExcepcion() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));

            assertThrows(EstadoInvalidoException.class,
                    () -> torneoService.cambiarEstado(1L, EstadoTorneo.FINALIZADO));

            verify(torneoRepository, never()).save(any());
        }

        @Test
        @DisplayName("BORRADOR → EN_CURSO: transición inválida (falta pasar por INSCRIPCION y SORTEADO)")
        void cambiarEstado_deBorradorAEnCurso_lanzaExcepcion() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));

            assertThrows(EstadoInvalidoException.class,
                    () -> torneoService.cambiarEstado(1L, EstadoTorneo.EN_CURSO));

            verify(torneoRepository, never()).save(any());
        }

        @Test
        @DisplayName("FINALIZADO → cualquier estado: no permite transición desde estado terminal")
        void cambiarEstado_desdeEstadoFinalizado_lanzaExcepcion() {
            torneoBase.setEstado(EstadoTorneo.FINALIZADO);
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));

            assertThrows(EstadoInvalidoException.class,
                    () -> torneoService.cambiarEstado(1L, EstadoTorneo.BORRADOR));

            verify(torneoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CANCELADO → cualquier estado: no permite transición desde estado terminal")
        void cambiarEstado_desdeEstadoCancelado_lanzaExcepcion() {
            torneoBase.setEstado(EstadoTorneo.CANCELADO);
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));

            assertThrows(EstadoInvalidoException.class,
                    () -> torneoService.cambiarEstado(1L, EstadoTorneo.INSCRIPCION));

            verify(torneoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Torneo inexistente lanza ResourceNotFoundException")
        void cambiarEstado_torneoNoExiste_lanzaExcepcion() {
            when(torneoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> torneoService.cambiarEstado(99L, EstadoTorneo.INSCRIPCION));

            verify(torneoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("actualizar()")
    class ActualizarTests {

        private TorneoRequest requestActualizacion;

        @BeforeEach
        void setUpRequest() {
            requestActualizacion = TorneoRequest.builder()
                    .nombre("Torneo Verano Actualizado")
                    .formato(FormatoTorneo.ELIMINACION_DIRECTA)
                    .fechaInicio(LocalDate.of(2025, 12, 5))
                    .tipoSorteo(TipoSorteo.ALEATORIO)
                    .build();
        }

        @Test
        @DisplayName("Torneo en BORRADOR permite edición")
        void actualizar_torneoEnBorrador_exitoso() {
            torneoBase.setEstado(EstadoTorneo.BORRADOR);
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));
            when(torneoMapper.torneoToResponse(torneoBase)).thenReturn(torneoResponseBase);

            torneoService.actualizar(1L, requestActualizacion);

            assertThat(torneoBase.getNombre()).isEqualTo("Torneo Verano Actualizado");
            verify(torneoRepository).save(torneoBase);
        }

        @Test
        @DisplayName("Torneo en INSCRIPCION no permite edición")
        void actualizar_torneoEnInscripcion_lanzaExcepcion() {
            torneoBase.setEstado(EstadoTorneo.INSCRIPCION);
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));

            assertThrows(EstadoInvalidoException.class,
                    () -> torneoService.actualizar(1L, requestActualizacion));

            verify(torneoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Torneo en EN_CURSO no permite edición")
        void actualizar_torneoEnCurso_lanzaExcepcion() {
            torneoBase.setEstado(EstadoTorneo.EN_CURSO);
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));

            assertThrows(EstadoInvalidoException.class,
                    () -> torneoService.actualizar(1L, requestActualizacion));

            verify(torneoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Torneo en FINALIZADO no permite edición")
        void actualizar_torneoFinalizado_lanzaExcepcion() {
            torneoBase.setEstado(EstadoTorneo.FINALIZADO);
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));

            assertThrows(EstadoInvalidoException.class,
                    () -> torneoService.actualizar(1L, requestActualizacion));

            verify(torneoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Torneo inexistente lanza ResourceNotFoundException")
        void actualizar_torneoNoExiste_lanzaExcepcion() {
            when(torneoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> torneoService.actualizar(99L, requestActualizacion));
        }
    }

    @Nested
    @DisplayName("crear()")
    class CrearTests {

        @Test
        @DisplayName("Nuevo torneo siempre se crea en estado BORRADOR")
        void crear_nuevoTorneo_estadoInicialEsBorrador() {
            TorneoRequest request = TorneoRequest.builder()
                    .nombre("Nuevo Torneo")
                    .formato(FormatoTorneo.ELIMINACION_DIRECTA)
                    .fechaInicio(LocalDate.of(2025, 12, 1))
                    .tipoSorteo(TipoSorteo.ALEATORIO)
                    .build();

            Torneo torneoNuevo = Torneo.builder()
                    .nombre("Nuevo Torneo")
                    .formato(FormatoTorneo.ELIMINACION_DIRECTA)
                    .fechaInicio(LocalDate.of(2025, 12, 1))
                    .tipoSorteo(TipoSorteo.ALEATORIO)
                    .build();

            when(torneoMapper.requestToTorneo(any(), any(), any())).thenReturn(torneoNuevo);
            when(torneoRepository.save(any())).thenReturn(torneoNuevo);
            when(torneoMapper.torneoToResponse(any())).thenReturn(
                    TorneoResponse.builder().estado(EstadoTorneo.BORRADOR).build());

            torneoService.crear(request);

            assertThat(torneoNuevo.getEstado()).isEqualTo(EstadoTorneo.BORRADOR);
            verify(torneoRepository).save(torneoNuevo);
        }

        @Test
        @DisplayName("Copia la plantilla de formato al torneo sin dejar dependencia dinámica")
        void crear_conPlantillaFormato_copiaValoresAlTorneo() {
            TorneoRequest request = TorneoRequest.builder()
                    .nombre("Nuevo Torneo")
                    .formato(FormatoTorneo.ELIMINACION_DIRECTA)
                    .fechaInicio(LocalDate.of(2025, 12, 1))
                    .tipoSorteo(TipoSorteo.ALEATORIO)
                    .plantillaFormatoId(5L)
                    .build();

            Torneo torneoNuevo = Torneo.builder()
                    .nombre("Nuevo Torneo")
                    .formato(FormatoTorneo.ELIMINACION_DIRECTA)
                    .fechaInicio(LocalDate.of(2025, 12, 1))
                    .tipoSorteo(TipoSorteo.ALEATORIO)
                    .build();

            PlantillaFormato plantilla = PlantillaFormato.builder()
                    .id(5L)
                    .nombre("12 parejas - 4 grupos")
                    .formatoTorneo(FormatoTorneo.MINITORNEO)
                    .tipoSorteo(TipoSorteo.CABEZAS_SERIE)
                    .cantidadParejasObjetivo(12)
                    .cantidadGrupos(4)
                    .parejasPorGrupo(3)
                    .avanzanPorGrupo(2)
                    .incluyeFaseGrupos(true)
                    .incluyeEliminacion(true)
                    .activo(true)
                    .build();

            when(torneoMapper.requestToTorneo(any(), any(), any())).thenReturn(torneoNuevo);
            when(plantillaFormatoRepository.findByIdAndActivoTrue(5L)).thenReturn(Optional.of(plantilla));
            when(torneoRepository.save(any())).thenReturn(torneoNuevo);
            when(torneoMapper.torneoToResponse(any())).thenReturn(torneoResponseBase);

            torneoService.crear(request);

            assertThat(torneoNuevo.getPlantillaFormatoId()).isEqualTo(5L);
            assertThat(torneoNuevo.getPlantillaFormatoNombre()).isEqualTo("12 parejas - 4 grupos");
            assertThat(torneoNuevo.getFormato()).isEqualTo(FormatoTorneo.MINITORNEO);
            assertThat(torneoNuevo.getTipoSorteo()).isEqualTo(TipoSorteo.CABEZAS_SERIE);
            assertThat(torneoNuevo.getCantidadParejasObjetivo()).isEqualTo(12);
            assertThat(torneoNuevo.getCantidadGrupos()).isEqualTo(4);
            assertThat(torneoNuevo.getParejasPorGrupo()).isEqualTo(3);
            assertThat(torneoNuevo.getAvanzanPorGrupo()).isEqualTo(2);
            assertThat(torneoNuevo.isIncluyeFaseGrupos()).isTrue();
            assertThat(torneoNuevo.isIncluyeEliminacion()).isTrue();
        }

        @Test
        @DisplayName("Copia rondas de plantilla de puntos a ConfiguracionPuntos del torneo")
        void crear_conPlantillaPuntos_copiaRondasAlTorneo() {
            TorneoRequest request = TorneoRequest.builder()
                    .nombre("Nuevo Torneo")
                    .formato(FormatoTorneo.MINITORNEO)
                    .fechaInicio(LocalDate.of(2025, 12, 1))
                    .tipoSorteo(TipoSorteo.ALEATORIO)
                    .plantillaPuntosId(7L)
                    .build();

            Torneo torneoNuevo = Torneo.builder()
                    .nombre("Nuevo Torneo")
                    .formato(FormatoTorneo.MINITORNEO)
                    .fechaInicio(LocalDate.of(2025, 12, 1))
                    .tipoSorteo(TipoSorteo.ALEATORIO)
                    .build();

            PlantillaPuntos plantilla = PlantillaPuntos.builder()
                    .id(7L)
                    .nombre("Ranking estandar")
                    .activo(true)
                    .build();
            plantilla.agregarRonda(PlantillaPuntosRonda.builder()
                    .nombreRonda("Grupos")
                    .puntosGanador(10)
                    .puntosPerdedor(5)
                    .orden(1)
                    .build());
            plantilla.agregarRonda(PlantillaPuntosRonda.builder()
                    .nombreRonda("Final")
                    .puntosGanador(100)
                    .puntosPerdedor(70)
                    .orden(2)
                    .build());

            when(torneoMapper.requestToTorneo(any(), any(), any())).thenReturn(torneoNuevo);
            when(plantillaPuntosRepository.findByIdAndActivoTrue(7L)).thenReturn(Optional.of(plantilla));
            when(torneoRepository.save(any())).thenReturn(torneoNuevo);
            when(torneoMapper.torneoToResponse(any())).thenReturn(torneoResponseBase);

            torneoService.crear(request);

            assertThat(torneoNuevo.getPlantillaPuntosId()).isEqualTo(7L);
            assertThat(torneoNuevo.getPlantillaPuntosNombre()).isEqualTo("Ranking estandar");
            assertThat(torneoNuevo.getConfiguracionPuntos()).hasSize(2);
            verify(configuracionPuntosRepository).saveAll(any());
        }

        @Test
        @DisplayName("Configuracion manual de puntos tiene prioridad sobre la plantilla elegida")
        void crear_conPlantillaYConfiguracionManual_priorizaManual() {
            TorneoRequest request = TorneoRequest.builder()
                    .nombre("Nuevo Torneo")
                    .formato(FormatoTorneo.MINITORNEO)
                    .fechaInicio(LocalDate.of(2025, 12, 1))
                    .tipoSorteo(TipoSorteo.ALEATORIO)
                    .plantillaPuntosId(7L)
                    .configuracionPuntos(List.of(ConfiguracionPuntosRequest.builder()
                            .nombreRonda("Final especial")
                            .puntosGanador(150)
                            .puntosPerdedor(90)
                            .orden(1)
                            .build()))
                    .build();

            Torneo torneoNuevo = Torneo.builder().build();
            PlantillaPuntos plantilla = PlantillaPuntos.builder()
                    .id(7L)
                    .nombre("Ranking estandar")
                    .activo(true)
                    .build();
            plantilla.agregarRonda(PlantillaPuntosRonda.builder()
                    .nombreRonda("Final")
                    .puntosGanador(100)
                    .puntosPerdedor(70)
                    .orden(1)
                    .build());

            when(torneoMapper.requestToTorneo(any(), any(), any())).thenReturn(torneoNuevo);
            when(plantillaPuntosRepository.findByIdAndActivoTrue(7L)).thenReturn(Optional.of(plantilla));
            when(torneoRepository.save(any())).thenReturn(torneoNuevo);
            when(torneoMapper.torneoToResponse(any())).thenReturn(torneoResponseBase);

            torneoService.crear(request);

            ConfiguracionPuntos config = torneoNuevo.getConfiguracionPuntos().get(0);
            assertThat(config.getNombreRonda()).isEqualTo("Final especial");
            assertThat(config.getPuntosGanador()).isEqualTo(150);
            assertThat(config.getPuntosPerdedor()).isEqualTo(90);
        }
    }

    @Nested
    @DisplayName("buscarPorId()")
    class BuscarPorIdTests {

        @Test
        @DisplayName("ID existente devuelve TorneoResponse correctamente")
        void buscarPorId_existente_devuelveResponse() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));
            when(torneoMapper.torneoToResponse(torneoBase)).thenReturn(torneoResponseBase);

            TorneoResponse resultado = torneoService.buscarPorId(1L);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getId()).isEqualTo(1L);
            assertThat(resultado.getNombre()).isEqualTo("Torneo Verano 2025");
        }

        @Test
        @DisplayName("ID inexistente lanza ResourceNotFoundException")
        void buscarPorId_inexistente_lanzaExcepcion() {
            when(torneoRepository.findById(99L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> torneoService.buscarPorId(99L));

            assertThat(ex.getMessage()).contains("99");
        }
    }

    @Nested
    @DisplayName("eliminar()")
    class EliminarTests {

        @Test
        @DisplayName("ID existente desactiva el torneo correctamente")
        void eliminar_existente_desactivaTorneo() {
            when(torneoRepository.findById(1L)).thenReturn(Optional.of(torneoBase));

            torneoService.eliminar(1L);

            assertThat(torneoBase.isActivo()).isFalse();
            verify(torneoRepository).save(torneoBase);
            verify(torneoRepository, never()).delete(any());
        }

        @Test
        @DisplayName("ID inexistente lanza ResourceNotFoundException sin guardar cambios")
        void eliminar_inexistente_lanzaExcepcion() {
            when(torneoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> torneoService.eliminar(99L));

            verify(torneoRepository, never()).save(any());
            verify(torneoRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("listarTodos()")
    class ListarTodosTests {

        @Test
        @DisplayName("Devuelve lista mapeada de todos los torneos")
        void listarTodos_devuelveLista() {
            List<Torneo> torneos = List.of(torneoBase);
            when(torneoRepository.findAllConRelaciones()).thenReturn(torneos);
            when(torneoMapper.torneoToResponse(torneoBase)).thenReturn(torneoResponseBase);

            List<TorneoResponse> resultado = torneoService.listarTodos();

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getNombre()).isEqualTo("Torneo Verano 2025");
        }

        @Test
        @DisplayName("Lista vacía cuando no hay torneos")
        void listarTodos_sinTorneos_devuelveListaVacia() {
            when(torneoRepository.findAllConRelaciones()).thenReturn(List.of());

            List<TorneoResponse> resultado = torneoService.listarTodos();

            assertThat(resultado).isEmpty();
        }
    }
}
