package com.padel.rankpadel.dto.response;

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
public class JugadorBusquedaResponse {

    private Long id;
    private String nombre;
    private String apellido;
    private String categoriaNombre;
    private com.padel.rankpadel.enums.Genero genero;
}
