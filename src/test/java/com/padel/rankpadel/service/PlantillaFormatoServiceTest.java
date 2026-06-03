package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.PlantillaFormatoRequest;
import com.padel.rankpadel.dto.response.PlantillaFormatoResponse;
import com.padel.rankpadel.entity.PlantillaFormato;
import com.padel.rankpadel.enums.FormatoTorneo;
import com.padel.rankpadel.enums.TipoSorteo;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.PlantillaFormatoMapper;
import com.padel.rankpadel.repository.PlantillaFormatoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlantillaFormatoService")
class PlantillaFormatoServiceTest {

    @Mock
    private PlantillaFormatoRepository plantillaFormatoRepository;

    @Mock
    private PlantillaFormatoMapper plantillaFormatoMapper;

    @InjectMocks
    private PlantillaFormatoService service;

    @Test
    @DisplayName("crear guarda una plantilla activa")
    void crear_guardaPlantilla() {
        PlantillaFormatoRequest request = requestBase();
        PlantillaFormato plantilla = plantillaBase();
        PlantillaFormatoResponse response = responseBase();

        when(plantillaFormatoMapper.requestToPlantilla(request)).thenReturn(plantilla);
        when(plantillaFormatoMapper.plantillaToResponse(plantilla)).thenReturn(response);

        PlantillaFormatoResponse result = service.crear(request);

        assertThat(result.getNombre()).isEqualTo("12 parejas");
        verify(plantillaFormatoRepository).save(plantilla);
    }

    @Test
    @DisplayName("listarTodos puede limitar a activas")
    void listarTodos_soloActivas() {
        PlantillaFormato plantilla = plantillaBase();
        when(plantillaFormatoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(plantilla));
        when(plantillaFormatoMapper.plantillaToResponse(plantilla)).thenReturn(responseBase());

        List<PlantillaFormatoResponse> result = service.listarTodos(true);

        assertThat(result).hasSize(1);
        verify(plantillaFormatoRepository).findByActivoTrueOrderByNombreAsc();
        verify(plantillaFormatoRepository, never()).findAllByOrderByNombreAsc();
    }

    @Test
    @DisplayName("actualizar modifica entidad existente")
    void actualizar_existente() {
        PlantillaFormato plantilla = plantillaBase();
        PlantillaFormatoRequest request = requestBase();
        when(plantillaFormatoRepository.findById(1L)).thenReturn(Optional.of(plantilla));
        when(plantillaFormatoMapper.plantillaToResponse(plantilla)).thenReturn(responseBase());

        service.actualizar(1L, request);

        verify(plantillaFormatoMapper).actualizarEntidad(plantilla, request);
        verify(plantillaFormatoRepository).save(plantilla);
    }

    @Test
    @DisplayName("eliminar desactiva la plantilla")
    void eliminar_desactiva() {
        PlantillaFormato plantilla = plantillaBase();
        when(plantillaFormatoRepository.findById(1L)).thenReturn(Optional.of(plantilla));

        service.eliminar(1L);

        assertThat(plantilla.isActivo()).isFalse();
        verify(plantillaFormatoRepository).save(plantilla);
    }

    @Test
    @DisplayName("buscar inexistente lanza ResourceNotFoundException")
    void buscar_inexistente() {
        when(plantillaFormatoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.buscarPorId(99L));
    }

    private PlantillaFormatoRequest requestBase() {
        return PlantillaFormatoRequest.builder()
                .nombre("12 parejas")
                .formatoTorneo(FormatoTorneo.MINITORNEO)
                .tipoSorteo(TipoSorteo.ALEATORIO)
                .cantidadParejasObjetivo(12)
                .cantidadGrupos(4)
                .parejasPorGrupo(3)
                .avanzanPorGrupo(2)
                .incluyeFaseGrupos(true)
                .incluyeEliminacion(true)
                .activo(true)
                .build();
    }

    private PlantillaFormato plantillaBase() {
        return PlantillaFormato.builder()
                .id(1L)
                .nombre("12 parejas")
                .formatoTorneo(FormatoTorneo.MINITORNEO)
                .tipoSorteo(TipoSorteo.ALEATORIO)
                .activo(true)
                .build();
    }

    private PlantillaFormatoResponse responseBase() {
        return PlantillaFormatoResponse.builder()
                .id(1L)
                .nombre("12 parejas")
                .formatoTorneo(FormatoTorneo.MINITORNEO)
                .tipoSorteo(TipoSorteo.ALEATORIO)
                .activo(true)
                .build();
    }
}
