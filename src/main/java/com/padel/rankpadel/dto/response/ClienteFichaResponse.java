package com.padel.rankpadel.dto.response;

import java.util.List;

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
public class ClienteFichaResponse {

    private ClienteResponse cliente;
    private List<ReservaResponse> historial;

    /** Lo que este cliente debe hoy, sumando la cancha y lo que consumió y no pagó. */
    private java.math.BigDecimal deuda;

    /** Cuánto dejó en el kiosco, aparte de las canchas. */
    private java.math.BigDecimal consumoKiosco;

    /** Las últimas compras del mostrador, para que la ficha muestre qué consume. */
    private List<VentaResponse> ultimasCompras;

    /** Abonos vigentes de este teléfono: el club necesita verlos junto al historial. */
    private List<TurnoFijoResponse> abonos;

    /**
     * Otros nombres con los que reservó este mismo teléfono. Vacío si siempre usó el de
     * la ficha. Sirve para detectar tanto un apodo como dos personas compartiendo número.
     */
    private List<String> otrosNombres;
}
