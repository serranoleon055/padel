package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.padel.rankpadel.dto.response.ImportacionResponse;
import com.padel.rankpadel.entity.Cliente;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.JugadorRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImportacionService - alta masiva desde la planilla del club")
class ImportacionServiceTest {

    @Mock
    private JugadorRepository jugadorRepository;
    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ImportacionService importacionService;

    @Nested
    @DisplayName("Jugadores")
    class Jugadores {

        @Test
        @DisplayName("La vista previa no guarda nada")
        void vistaPrevia_noEscribe() {
            lenient().when(jugadorRepository.existsByActivoTrueAndNombreNormalizado(anyString())).thenReturn(false);

            ImportacionResponse resultado = importacionService.importarJugadores("""
                    nombre;apellido;telefono
                    Juan;Perez;3856894061
                    """, true);

            assertThat(resultado.isVistaPrevia()).isTrue();
            assertThat(resultado.getNuevos()).isEqualTo(1);
            verify(jugadorRepository, never()).save(any());
        }

        @Test
        @DisplayName("Confirmada, guarda un jugador por fila nueva")
        void confirmada_guarda() {
            when(jugadorRepository.existsByActivoTrueAndNombreNormalizado(anyString())).thenReturn(false);

            ImportacionResponse resultado = importacionService.importarJugadores("""
                    nombre;apellido;telefono
                    Juan;Perez;3856894061
                    Ana;Gomez;3854445555
                    """, false);

            assertThat(resultado.getNuevos()).isEqualTo(2);
            verify(jugadorRepository, times(2)).save(any(Jugador.class));
        }

        @Test
        @DisplayName("Un jugador que ya existe se saltea, no se duplica")
        void yaExiste_seSaltea() {
            when(jugadorRepository.existsByActivoTrueAndNombreNormalizado(anyString())).thenReturn(true);

            ImportacionResponse resultado = importacionService.importarJugadores(
                    "nombre;apellido\nJuan;Perez", false);

            assertThat(resultado.getRepetidos()).isEqualTo(1);
            assertThat(resultado.getNuevos()).isZero();
            verify(jugadorRepository, never()).save(any());
        }

        @Test
        @DisplayName("El mismo teléfono repetido dentro del archivo entra una sola vez")
        void repetidoEnElArchivo_entraUnaVez() {
            // La planilla del club trae al mismo dos veces más seguido de lo que parece.
            when(jugadorRepository.existsByActivoTrueAndNombreNormalizado(anyString())).thenReturn(false);

            ImportacionResponse resultado = importacionService.importarJugadores("""
                    nombre;apellido;telefono
                    Juan;Perez;385 689 4061
                    Juan Carlos;Perez Lopez;3856894061
                    """, false);

            assertThat(resultado.getNuevos()).isEqualTo(1);
            assertThat(resultado.getRepetidos()).isEqualTo(1);
            verify(jugadorRepository, times(1)).save(any(Jugador.class));
        }

        @Test
        @DisplayName("Una fila sin nombre se marca como error y no frena el resto")
        void sinNombre_esError() {
            when(jugadorRepository.existsByActivoTrueAndNombreNormalizado(anyString())).thenReturn(false);

            ImportacionResponse resultado = importacionService.importarJugadores("""
                    nombre;telefono
                    ;3856894061
                    Ana;3854445555
                    """, false);

            assertThat(resultado.getConError()).isEqualTo(1);
            assertThat(resultado.getNuevos()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Clientes")
    class Clientes {

        @Test
        @DisplayName("Sin teléfono no se puede crear la ficha: es lo que identifica al cliente")
        void sinTelefono_esError() {
            ImportacionResponse resultado = importacionService.importarClientes(
                    "nombre;telefono\nJuan;", false);

            assertThat(resultado.getConError()).isEqualTo(1);
            verify(clienteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Un teléfono que ya tiene ficha se saltea aunque esté escrito distinto")
        void telefonoConFicha_seSaltea() {
            when(clienteRepository.findByTelefonoNormalizado("3856894061"))
                    .thenReturn(Optional.of(new Cliente()));

            ImportacionResponse resultado = importacionService.importarClientes(
                    "nombre;telefono\nJuan;+54 9 385 15 689-4061", false);

            assertThat(resultado.getRepetidos()).isEqualTo(1);
            verify(clienteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Un cliente nuevo se guarda con el teléfono tal como lo escribieron")
        void nuevo_guarda() {
            when(clienteRepository.findByTelefonoNormalizado(anyString())).thenReturn(Optional.empty());

            ImportacionResponse resultado = importacionService.importarClientes(
                    "nombre;telefono;email\nAna;3854445555;ana@mail.com", false);

            assertThat(resultado.getNuevos()).isEqualTo(1);
            verify(clienteRepository).save(any(Cliente.class));
        }
    }

    @Test
    @DisplayName("Un archivo con cabecera pero sin filas se rechaza explicándolo")
    void sinFilas_lanza() {
        assertThatThrownBy(() -> importacionService.importarJugadores("nombre;telefono", true))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("ninguna fila");
    }
}
