package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class AdminRequest {

    @NotBlank
    @Size(max = 80)
    private String username;

    // Mínimo alineado (y por encima) del que exige AdminBootstrap en producción:
    // la clave del panel es la llave de todos los datos del club.
    @Size(min = 10, max = 120)
    private String password;
}
