package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.padel.rankpadel.dto.request.PlantillaPuntosRequest;
import com.padel.rankpadel.dto.request.PlantillaPuntosRondaRequest;
import com.padel.rankpadel.dto.response.PlantillaPuntosResponse;
import com.padel.rankpadel.entity.PlantillaPuntos;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.PlantillaPuntosMapper;
import com.padel.rankpadel.repository.PlantillaPuntosRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlantillaPuntosService")
class PlantillaPuntosServiceTest {

    @Mock
    private PlantillaPuntosRepository plantillaPuntosRepository;

    @Mock
    private PlantillaPuntosMapper plantillaPuntosMapper;

    @InjectMocks
    private PlantillaPuntosService service;

    @Test
    @DisplayName("crear guarda plantilla con rondas")
    void crear_guardaPlantilla() {
        PlantillaPuntosRequest request = requestBase();
        PlantillaPuntos plantilla = plantillaBase();
        PlantillaPuntosResponse response = PlantillaPuntosResponse.builder().id(1L).nombre("Ranking").build();

        when(plantillaPuntosMapper.requestToPlantilla(request)).thenReturn(plantilla);
        when(plantillaPuntosMapper.plantillaToResponse(plantilla)).thenReturn(response);

        PlantillaPuntosResponse result = service.crear(request);

        assertThat(result.getNombre()).isEqualTo("Ranking");
        verify(plantillaPuntosRepository).save(plantilla);
    }

    @Test
    @DisplayName("listarTodos devuelve plantillas ordenadas")
    void listarTodos() {
        PlantillaPuntos plantilla = plantillaBase();
        when(plantillaPuntosRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(plantilla));
        when(plantillaPuntosMapper.plantillaToResponse(plantilla))
                .thenReturn(PlantillaPuntosResponse.builder().id(1L).nombre("Ranking").build());

        List<PlantillaPuntosResponse> result = service.listarTodos(false);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("actualizar reemplaza datos y rondas")
    void actualizar() {
        PlantillaPuntos plantilla = plantillaBase();
        PlantillaPuntosRequest request = requestBase();
        when(plantillaPuntosRepository.findById(1L)).thenReturn(Optional.of(plantilla));
        when(plantillaPuntosMapper.plantillaToResponse(plantilla))
                .thenReturn(PlantillaPuntosResponse.builder().id(1L).nombre("Ranking").build());

        service.actualizar(1L, request);

        verify(plantillaPuntosMapper).actualizarEntidad(plantilla, request);
        verify(plantillaPuntosRepository).save(plantilla);
    }

    @Test
    @DisplayName("eliminar desactiva la plantilla")
    void eliminar() {
        PlantillaPuntos plantilla = plantillaBase();
        when(plantillaPuntosRepository.findById(1L)).thenReturn(Optional.of(plantilla));

        service.eliminar(1L);

        assertThat(plantilla.isActivo()).isFalse();
        verify(plantillaPuntosRepository).save(plantilla);
    }

    @Test
    @DisplayName("buscar inexistente lanza ResourceNotFoundException")
    void buscar_inexistente() {
        when(plantillaPuntosRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.buscarPorId(99L));
    }

    private PlantillaPuntosRequest requestBase() {
        return PlantillaPuntosRequest.builder()
                .nombre("Ranking")
                .activo(true)
                .rondas(List.of(PlantillaPuntosRondaRequest.builder()
                        .nombreRonda("Final")
                        .puntosGanador(100)
                        .puntosPerdedor(70)
                        .orden(1)
                        .build()))
                .build();
    }

    private PlantillaPuntos plantillaBase() {
        return PlantillaPuntos.builder()
                .id(1L)
                .nombre("Ranking")
                .activo(true)
                .build();
    }
}
