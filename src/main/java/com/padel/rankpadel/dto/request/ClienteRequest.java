package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClienteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120)
    private String nombre;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 40)
    private String telefono;

    @Email(message = "El email no tiene un formato válido")
    @Size(max = 120)
    private String email;

    @Size(max = 500)
    private String notas;
}
