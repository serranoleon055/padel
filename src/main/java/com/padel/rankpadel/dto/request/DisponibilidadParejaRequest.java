package com.padel.rankpadel.dto.request;

import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.dto.RestriccionHorariaDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DisponibilidadParejaRequest {

    @Valid
    @Size(max = 30, message = "Son demasiados horarios: resumí el resto en la aclaración")
    private List<RestriccionHorariaDto> restricciones = new ArrayList<>();

    @Size(max = 500)
    private String notas;
}
