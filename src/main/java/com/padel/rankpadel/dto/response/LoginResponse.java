package com.padel.rankpadel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Respuesta del login con el token JWT para usar en requests protegidos")
public class LoginResponse {

    @Schema(description = "Token JWT. Usarlo en el header: Authorization: Bearer <token>", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9...")
    private String token;

    @Schema(description = "DUENIO o MOSTRADOR. El panel lo usa para saber qué mostrar; "
            + "quien decide qué se puede hacer es el backend.", example = "DUENIO")
    private String rol;

    @Schema(description = "Usuario que entró, para mostrarlo en el panel.", example = "admin")
    private String usuario;
}
