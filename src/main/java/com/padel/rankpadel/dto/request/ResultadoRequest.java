package com.padel.rankpadel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
public class ResultadoRequest {

    @NotBlank
    @Schema(description = "Sets separados por barra, cada set con guión", example = "6-3 / 3-6 / 7-5")
    private String marcador;

}
