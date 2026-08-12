package com.padel.rankpadel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Si los avisos por mail al club están funcionando y a dónde llegan. Sin esto el panel no
 * daba ninguna señal: la función existía pero nadie sabía si estaba prendida.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EstadoNotificacionesResponse {

    /** Hay un servidor de correo cargado en el servidor. */
    private boolean servidorConfigurado;

    /** Mail al que llegan los avisos, o null si todavía no hay ninguno. */
    private String destino;

    /** De dónde sale el destino: "sede" (Configuración de sede) o "variable". */
    private String origenDestino;

    /** Los avisos salen de verdad. */
    private boolean activo;
}
