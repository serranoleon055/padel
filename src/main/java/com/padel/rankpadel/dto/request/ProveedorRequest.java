package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProveedorRequest {

    @NotBlank(message = "El nombre del proveedor es obligatorio")
    @Size(max = 120)
    private String nombre;

    @Size(max = 40)
    private String telefono;

    @Size(max = 300)
    private String notas;

    private Boolean activo;
}
