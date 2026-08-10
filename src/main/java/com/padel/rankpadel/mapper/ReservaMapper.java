package com.padel.rankpadel.mapper;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;

/**
 * Está aparte de {@code ReservaService} porque {@code ClienteService} también arma
 * respuestas de reserva (el historial de la ficha) y los dos servicios se necesitan
 * mutuamente: sin el mapper habría una dependencia circular.
 */
@Component
public class ReservaMapper {

    public ReservaResponse aResponse(Reserva reserva) {
        Pago pago = reserva.getPago();
        return ReservaResponse.builder()
                .id(reserva.getId())
                .canchaId(reserva.getCancha() != null ? reserva.getCancha().getId() : null)
                .canchaNombre(reserva.getCancha() != null ? reserva.getCancha().getNombre() : null)
                .fecha(reserva.getFecha())
                .horaInicio(reserva.getHoraInicio())
                .horaFin(reserva.getHoraFin())
                .estado(reserva.getEstado() != null ? reserva.getEstado().name() : null)
                .clienteNombre(reserva.getClienteNombre())
                .clienteTelefono(reserva.getClienteTelefono())
                .codigo(reserva.getCodigo())
                .estadoPago(pago != null && pago.getEstado() != null ? pago.getEstado().name() : null)
                .montoSenia(pago != null ? pago.getMontoSenia() : null)
                .montoTotal(pago != null ? pago.getMontoTotal() : null)
                .precioAplicado(reserva.getPrecioAplicado())
                .turnoFijo(reserva.getTurnoFijo() != null)
                .clienteId(reserva.getCliente() != null ? reserva.getCliente().getId() : null)
                .build();
    }
}
