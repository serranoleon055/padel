package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una duración que se puede reservar a partir de cierta hora, con lo que sale. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OpcionDuracion {

    private int minutos;
    private LocalTime horaFin;
    /** Precio del turno completo. Null si la cancha no tiene tarifa cargada. */
    private BigDecimal precio;
}
