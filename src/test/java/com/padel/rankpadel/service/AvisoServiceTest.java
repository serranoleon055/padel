package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.request.AvisoEnviadoRequest;
import com.padel.rankpadel.entity.AvisoEnviado;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.AvisoEnviadoRepository;
import com.padel.rankpadel.repository.TorneoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvisoService - a quién ya se le avisó")
class AvisoServiceTest {

    @Mock private AvisoEnviadoRepository avisoEnviadoRepository;
    @Mock private TorneoRepository torneoRepository;

    @InjectMocks
    private AvisoService service;

    private AvisoEnviadoRequest pedido(String clave) {
        AvisoEnviadoRequest request = new AvisoEnviadoRequest();
        request.setJugadorId(7L);
        request.setTema("DIA:2026-11-14");
        request.setClave(clave);
        return request;
    }

    @Test
    @DisplayName("Volver a avisar pisa la huella del aviso anterior en vez de sumar otro")
    void reemplaza() {
        AvisoEnviado anterior = AvisoEnviado.builder().id(1L).torneoId(3L).jugadorId(7L).tema("DIA:2026-11-14").clave("vieja").build();
        when(torneoRepository.existsById(3L)).thenReturn(true);
        when(avisoEnviadoRepository.findByTorneoIdAndJugadorIdAndTema(3L, 7L, "DIA:2026-11-14")).thenReturn(Optional.of(anterior));
        when(avisoEnviadoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var respuesta = service.registrar(3L, pedido("nueva"));

        assertThat(respuesta.getClave()).isEqualTo("nueva");
        assertThat(anterior.getClave()).isEqualTo("nueva");
        assertThat(anterior.getEnviadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Torneo inexistente")
    void torneoInexistente() {
        when(torneoRepository.existsById(3L)).thenReturn(false);
        assertThatThrownBy(() -> service.registrar(3L, pedido("x"))).isInstanceOf(ResourceNotFoundException.class);
    }
}
