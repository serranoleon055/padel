package com.padel.rankpadel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Credenciales para autenticarse como administrador")
public class LoginRequest {

    @NotBlank
    @Schema(description = "Nombre de usuario del administrador", example = "admin")
    private String username;

    @NotBlank
    @Schema(description = "Contraseña del administrador", example = "admin123")
    private String password;
}
