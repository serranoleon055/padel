package com.padel.rankpadel.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class LoteReservaRequest {

    @NotNull
    private Long canchaId;

    @NotNull
    private LocalDate fecha;

    @NotNull
    private LocalTime horaInicio;

    /** Minutos del turno. Null = la duración más corta que vende el club. */
    private Integer duracionMin;

    @NotBlank
    private String clienteNombre;

    @NotBlank
    private String clienteTelefono;

    /** Opcional: a donde va el comprobante con el enlace del turno y el recordatorio. */
    @Email
    @Size(max = 160)
    private String clienteEmail;
}
