package com.padel.rankpadel.mapper;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.request.JugadorRequest;
import com.padel.rankpadel.dto.response.JugadorResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;

@Component
public class JugadorMapper {

    public Jugador requestToJugador(JugadorRequest jugadorRequest, Categoria categoria) {
        Jugador jugador = Jugador.builder()
                .nombre(jugadorRequest.getNombre())
                .apellido(jugadorRequest.getApellido())
                .genero(jugadorRequest.getGenero())
                .fotoUrl(jugadorRequest.getFotoUrl())
                .telefono(jugadorRequest.getTelefono())
                .fechaNacimiento(jugadorRequest.getFechaNacimiento())
                .posicionJuego(jugadorRequest.getPosicionJuego())
                .fechaRegistro(LocalDate.now())
                .categoria(categoria)
                .build();

        return jugador;
    }

    public JugadorResponse jugadorToResponse(Jugador jugador) {
        JugadorResponse respuesta = JugadorResponse.builder()
                .id(jugador.getId())
                .nombre(jugador.getNombre())
                .apellido(jugador.getApellido())
                .genero(jugador.getGenero())
                .fotoUrl(jugador.getFotoUrl())
                .fechaRegistro(jugador.getFechaRegistro())
                .categoriaId(getCategoriaId(jugador))
                .categoriaNombre(getCategoriaNombre(jugador))
                .posicionJuego(jugador.getPosicionJuego())
                .build();

        return respuesta;
    }

    private Long getCategoriaId(Jugador jugador) {
        try {
            return jugador.getCategoria() != null ? jugador.getCategoria().getId() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private String getCategoriaNombre(Jugador jugador) {
        try {
            return jugador.getCategoria() != null ? jugador.getCategoria().getNombre() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

}
