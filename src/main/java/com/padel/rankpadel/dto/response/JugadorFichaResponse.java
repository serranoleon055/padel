package com.padel.rankpadel.dto.response;

import java.time.LocalDate;

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
public class JugadorFichaResponse {

    private Long id;
    private LocalDate fechaNacimiento;
    private String telefono;
}
