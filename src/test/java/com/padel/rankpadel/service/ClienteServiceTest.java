package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.padel.rankpadel.dto.request.ClienteRequest;
import com.padel.rankpadel.entity.Cliente;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.mapper.ReservaMapper;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClienteService - el teléfono es la identidad de la ficha")
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private ReservaMapper reservaMapper;

    @InjectMocks
    private ClienteService clienteService;

    private Cliente ficha(Long id, String nombre, String telefono) {
        return Cliente.builder().id(id).nombre(nombre).telefono(telefono).build();
    }

    private ClienteRequest pedido(String nombre, String telefono) {
        ClienteRequest request = new ClienteRequest();
        request.setNombre(nombre);
        request.setTelefono(telefono);
        return request;
    }

    @Test
    @DisplayName("Corregir el teléfono al de otra ficha se rechaza con el nombre del dueño")
    void actualizar_telefonoDeOtraFicha_rechaza() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(ficha(1L, "Juan", "3851111111")));
        when(clienteRepository.findByTelefonoNormalizado(any()))
                .thenReturn(Optional.of(ficha(2L, "Gonzalo", "3856894061")));

        assertThatThrownBy(() -> clienteService.actualizar(1L, pedido("Juan", "3856894061")))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("Gonzalo");
        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Guardar la misma ficha sin cambiarle el teléfono no se pisa a sí misma")
    void actualizar_mismoTelefono_guarda() {
        Cliente cliente = ficha(1L, "Juan", "3851111111");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(clienteRepository.findByTelefonoNormalizado(any())).thenReturn(Optional.of(cliente));

        clienteService.actualizar(1L, pedido("Juan Pérez", "3851111111"));

        assertThat(cliente.getNombre()).isEqualTo("Juan Pérez");
        verify(clienteRepository).save(cliente);
    }

    @Test
    @DisplayName("Un teléfono que no sirve como identidad no crea ficha en vez de fallar")
    void buscarOCrear_telefonoInservible_devuelveNull() {
        assertThat(clienteService.buscarOCrear("Juan", "-")).isNull();
        verify(clienteRepository, never()).save(any());
    }
}
