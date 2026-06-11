package com.padel.rankpadel.dto.request;

import java.time.LocalDate;

import com.padel.rankpadel.enums.Genero;

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
public class IntegranteInscripcionRequest {

    private Long jugadorId;
    private String nombre;
    private String apellido;
    private Genero genero;
    private String telefono;
    private LocalDate fechaNacimiento;
}
