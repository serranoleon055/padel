package com.padel.rankpadel.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lo contado, de a varios renglones.
 *
 * <p>Va en lote y no renglón por renglón porque así se cuenta: con el celular en la mano
 * recorriendo la góndola, y una conexión que se corta no puede dejar media planilla
 * cargada y media no.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConteoRequest {

    @NotEmpty(message = "No mandaste ningún renglón")
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        @NotNull(message = "Falta el renglón")
        private Long itemId;

        /**
         * Lo contado. Null borra el conteo de ese renglón y lo deja sin contar, que es
         * distinto de cargar un cero.
         */
        @Min(value = 0, message = "No se puede contar menos de cero")
        private Integer stockContado;
    }
}
