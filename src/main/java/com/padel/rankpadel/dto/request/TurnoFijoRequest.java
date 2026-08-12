package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Builder
@lombok.AllArgsConstructor
public class TurnoFijoRequest {

    @NotNull(message = "Elegí la cancha")
    private Long canchaId;

    @NotNull(message = "Elegí el día de la semana")
    @Min(value = 1, message = "El día debe ir de 1 (lunes) a 7 (domingo)")
    @Max(value = 7, message = "El día debe ir de 1 (lunes) a 7 (domingo)")
    private Integer diaSemana;

    @NotNull(message = "Indicá la hora de inicio")
    private LocalTime horaInicio;

    /**
     * Minutos del turno. El tope de verdad —que la cancha venda esa duración— lo pone
     * {@code TurnoFijoService.duracionValida}, que además nombra las que sí se venden.
     * Acá van solo los bordes groseros.
     *
     * <p>Ojo: este campo se llamaba {@code slots} y contaba horarios, no minutos. Al
     * renombrarlo quedó el {@code @Max(6)} de entonces, y como el front manda 60, 90 o
     * 120, <b>toda alta de abono respondía 400</b> con un mensaje que hablaba de
     * "6 horarios seguidos". Si se vuelve a tocar la unidad, tocar también estos límites.
     */
    @Min(value = 30, message = "El turno tiene que ser de al menos 30 minutos")
    @Max(value = 360, message = "Un turno fijo no puede ser de más de 6 horas")
    private Integer duracionMin;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(max = 120)
    private String clienteNombre;

    @NotBlank(message = "El teléfono del cliente es obligatorio")
    @Size(max = 40)
    private String clienteTelefono;

    /** Precio pactado del turno completo. Null = tarifa vigente de la cancha. */
    private BigDecimal precioPactado;

    @NotNull(message = "Indicá desde cuándo rige")
    private LocalDate vigenteDesde;

    private LocalDate vigenteHasta;

    @Size(max = 300)
    private String notas;
}
