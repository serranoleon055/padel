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
public class SponsorResponse {

    private Long id;
    private String nombre;
    private String logoUrl;
    private String enlace;
    private Long lugarId;
    private String lugarNombre;
    private int orden;
    private boolean activo;
}
