package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SponsorRequest {

    @NotBlank(message = "El nombre del sponsor es obligatorio")
    @Size(max = 120)
    private String nombre;

    @NotBlank(message = "Subí el logo del sponsor")
    @Size(max = 500)
    private String logoUrl;

    @Size(max = 500)
    private String enlace;

    /** Null = se muestra en todas las sedes. */
    private Long lugarId;

    @Min(value = 0, message = "El orden no puede ser negativo")
    private int orden;

    private Boolean activo;
}
