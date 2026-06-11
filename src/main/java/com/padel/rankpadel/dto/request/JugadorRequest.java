package com.padel.rankpadel.dto.request;

import java.time.LocalDate;

import com.padel.rankpadel.enums.Genero;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Datos para crear o actualizar un jugador")
public class JugadorRequest {

    @NotBlank
    @Schema(description = "Nombre del jugador", example = "Carlos")
    private String nombre;

    @NotBlank
    @Schema(description = "Apellido del jugador", example = "García")
    private String apellido;

    @NotNull
    @Schema(description = "Género del jugador", example = "MASCULINO")
    private Genero genero;

    @Schema(description = "URL de la foto del jugador (opcional)", example = "https://cdn.rankpadel.com/fotos/carlos.jpg")
    private String fotoUrl;

    @Schema(description = "ID de la categoría asignada (opcional)", example = "1")
    private Long categoriaId;

    @Schema(description = "Teléfono de contacto (opcional)")
    private String telefono;

    @Schema(description = "Fecha de nacimiento (opcional)")
    private LocalDate fechaNacimiento;
}
