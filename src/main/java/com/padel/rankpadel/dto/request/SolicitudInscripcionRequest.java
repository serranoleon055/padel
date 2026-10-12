package com.padel.rankpadel.dto.request;

import java.util.List;

import com.padel.rankpadel.dto.RestriccionHorariaDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
public class SolicitudInscripcionRequest {

    @NotNull
    private Long categoriaId;

    private String telefonoContacto;

    @NotNull
    @Valid
    private IntegranteInscripcionRequest jugador1;

    @NotNull
    @Valid
    private IntegranteInscripcionRequest jugador2;

    /** Cuándo no pueden jugar. Opcional: el que no carga nada queda disponible siempre. */
    @Valid
    @Size(max = 30, message = "Son demasiados horarios: resumí el resto en la aclaración")
    private List<RestriccionHorariaDto> restricciones;

    @Size(max = 500)
    private String restriccionesNotas;
}
