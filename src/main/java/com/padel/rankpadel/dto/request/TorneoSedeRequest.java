package com.padel.rankpadel.dto.request;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.dto.FranjaSedeDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TorneoSedeRequest {

    @NotNull(message = "Elegí el club")
    private Long lugarId;

    /** Las canchas del club que se usan en el torneo. Vacío = todas las activas. */
    private List<Long> canchaIds = new ArrayList<>();

    @Valid
    private List<FranjaSedeDto> franjas = new ArrayList<>();

    @DecimalMin(value = "0", message = "El monto acordado no puede ser negativo")
    private BigDecimal montoAcordado;

    @Size(max = 500)
    private String notas;
}
