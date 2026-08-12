package com.padel.rankpadel.mapper;

import java.time.LocalDate;
import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.response.TurnoFijoResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.TurnoFijo;
import com.padel.rankpadel.repository.ReservaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Está aparte de {@code TurnoFijoService} porque la ficha del cliente también muestra
 * los abonos, y {@code TurnoFijoService} depende de {@code ReservaService}, que a su vez
 * depende de {@code ClienteService}: inyectar el servicio ahí cerraría un ciclo. Mismo
 * motivo que {@link ReservaMapper}.
 */
@Component
@RequiredArgsConstructor
public class TurnoFijoMapper {

    private final ReservaRepository reservaRepository;

    public TurnoFijoResponse aResponse(TurnoFijo turnoFijo) {
        Cancha cancha = turnoFijo.getCancha();
        LocalDate generadoHasta = reservaRepository
                .findFechasGeneradas(turnoFijo.getId(), LocalDate.now()).stream()
                .max(Comparator.naturalOrder())
                .orElse(null);

        return TurnoFijoResponse.builder()
                .id(turnoFijo.getId())
                .canchaId(cancha.getId())
                .canchaNombre(cancha.getNombre())
                .lugarId(cancha.getLugar() != null ? cancha.getLugar().getId() : null)
                .lugarNombre(cancha.getLugar() != null ? cancha.getLugar().getNombre() : null)
                .diaSemana(turnoFijo.getDiaSemana())
                .horaInicio(turnoFijo.getHoraInicio())
                .horaFin(turnoFijo.getHoraInicio().plusMinutes(turnoFijo.getDuracionMin()))
                .duracionMin(turnoFijo.getDuracionMin())
                .clienteNombre(turnoFijo.getClienteNombre())
                .clienteTelefono(turnoFijo.getClienteTelefono())
                .precioPactado(turnoFijo.getPrecioPactado())
                .vigenteDesde(turnoFijo.getVigenteDesde())
                .vigenteHasta(turnoFijo.getVigenteHasta())
                .activo(turnoFijo.isActivo())
                .notas(turnoFijo.getNotas())
                .generadoHasta(generadoHasta)
                .build();
    }
}
