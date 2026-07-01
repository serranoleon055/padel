package com.padel.rankpadel.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.JugadorRequest;
import com.padel.rankpadel.dto.response.JugadorResponse;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.mapper.JugadorMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("JugadorService - detección de duplicados por nombre")
class JugadorServiceTest {

    @Mock
    private JugadorRepository jugadorRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private JugadorMapper jugadorMapper;

    @InjectMocks
    private JugadorService jugadorService;

    private JugadorRequest pedido(String nombre, String apellido) {
        return JugadorRequest.builder().nombre(nombre).apellido(apellido).genero(Genero.MASCULINO).build();
    }

    @Test
    @DisplayName("Crear con nombre ya existente (sin importar mayúsculas) lanza EstadoInvalidoException")
    void crear_nombreDuplicado_lanzaExcepcion() {
        when(jugadorRepository.existsByActivoTrueAndNombreNormalizado("santiago ruiz")).thenReturn(true);

        assertThrows(EstadoInvalidoException.class, () -> jugadorService.crear(pedido("Santiago", "Ruiz")));

        verify(jugadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear con nombre nuevo guarda el jugador")
    void crear_nombreNuevo_guarda() {
        when(jugadorRepository.existsByActivoTrueAndNombreNormalizado(anyString())).thenReturn(false);
        when(jugadorMapper.requestToJugador(any(), any())).thenReturn(new Jugador());
        when(jugadorMapper.jugadorToResponse(any())).thenReturn(JugadorResponse.builder().build());

        jugadorService.crear(pedido("Lucas", "Gomez"));

        verify(jugadorRepository).save(any(Jugador.class));
    }
}
