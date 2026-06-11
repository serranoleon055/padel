package com.padel.rankpadel.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class LoteReservaRequest {

    @NotNull
    private Long canchaId;

    @NotNull
    private LocalDate fecha;

    @NotEmpty
    private List<LocalTime> horarios;

    @NotBlank
    private String clienteNombre;

    @NotBlank
    private String clienteTelefono;
}
