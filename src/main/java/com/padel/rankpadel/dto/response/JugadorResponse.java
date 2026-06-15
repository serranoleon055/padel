package com.padel.rankpadel.dto.response;

import java.time.LocalDate;

import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.enums.PosicionJuego;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JugadorResponse {

    private Long id;
    private String nombre;
    private String apellido;
    private Genero genero;
    private String fotoUrl;
    private LocalDate fechaRegistro;
    private Long categoriaId;
    private String categoriaNombre;
    private PosicionJuego posicionJuego;

}
