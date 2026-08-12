package com.padel.rankpadel.dto.response;

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
public class ProveedorResponse {

    private Long id;
    private String nombre;
    private String telefono;
    private String notas;
    private boolean activo;

    /** Cuántos productos activos le compra el club. */
    private long productos;
}
