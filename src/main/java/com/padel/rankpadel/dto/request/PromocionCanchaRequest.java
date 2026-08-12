package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PromocionCanchaRequest {

    @NotNull(message = "Elegí la cancha")
    private Long canchaId;

    @NotBlank(message = "Poné un nombre a la promoción")
    @Size(max = 60)
    private String nombre;

    @NotBlank(message = "Elegí al menos un día")
    @Pattern(regexp = "^[1-7](,[1-7])*$", message = "Los días van de 1 (lunes) a 7 (domingo), separados por coma")
    private String diasSemana;

    @NotNull(message = "Indicá desde qué hora rige")
    private LocalTime horaDesde;

    @NotNull(message = "Indicá hasta qué hora rige")
    private LocalTime horaHasta;

    @NotNull(message = "Indicá el precio por hora")
    @DecimalMin(value = "0.01", message = "El precio tiene que ser mayor a cero")
    private BigDecimal precioPorHora;

    /** Null = arranca ya. */
    private LocalDate vigenteDesde;

    /** Null = sigue hasta que la den de baja. */
    private LocalDate vigenteHasta;
}
