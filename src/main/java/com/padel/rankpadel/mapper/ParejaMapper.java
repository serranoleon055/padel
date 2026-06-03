package com.padel.rankpadel.mapper;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.request.ParejaRequest;
import com.padel.rankpadel.dto.response.ParejaResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPareja;

@Component
public class ParejaMapper {

    public Pareja requestToPareja(ParejaRequest parejaRequest, Jugador jugador1, Jugador jugador2, Categoria categoria,
            Torneo torneo) {
        Pareja pareja = Pareja.builder()
                .esCabezaDeSerie(parejaRequest.isEsCabezaDeSerie())
                .estado(EstadoPareja.ACTIVA)
                .torneo(torneo)
                .jugador1(jugador1)
                .jugador2(jugador2)
                .categoria(categoria)
                .build();

        return pareja;
    }

    public ParejaResponse parejaToResponse(Pareja pareja) {
        ParejaResponse parejaDTO = ParejaResponse.builder()
                .id(pareja.getId())
                .esCabezaDeSerie(pareja.isEsCabezaDeSerie())
                .estado(pareja.getEstado())
                .jugador1Nombre(getJugador1Nombre(pareja))
                .jugador2Nombre(getJugador2Nombre(pareja))
                .categoriaNombre(getParejaCategoriaNombre(pareja))
                .build();

        return parejaDTO;
    }

    private String getJugador1Nombre(Pareja pareja) {
        try {
            return pareja.getJugador1() != null
                    ? pareja.getJugador1().getNombre() + " " + pareja.getJugador1().getApellido()
                    : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private String getJugador2Nombre(Pareja pareja) {
        try {
            return pareja.getJugador2() != null
                    ? pareja.getJugador2().getNombre() + " " + pareja.getJugador2().getApellido()
                    : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    private String getParejaCategoriaNombre(Pareja pareja) {
        try {
            return pareja.getCategoria() != null ? pareja.getCategoria().getNombre() : null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

}
