package com.padel.rankpadel.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.RestriccionHorariaDto;
import com.padel.rankpadel.dto.request.DisponibilidadParejaRequest;
import com.padel.rankpadel.dto.response.DisponibilidadParejaResponse;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.RestriccionHoraria;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.ParejaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Cuándo no puede jugar cada pareja. La pareja lo carga al inscribirse, el organizador lo
 * corrige con lo que le llega por WhatsApp, y el armado del fixture lo respeta.
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadParejaService {

    private static final String[] DIAS = { "", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo" };

    private final ParejaRepository parejaRepository;

    @Transactional(readOnly = true)
    public List<DisponibilidadParejaResponse> listar(Long torneoId) {
        return parejaRepository.findByTorneoId(torneoId).stream()
                .sorted(Comparator.comparing(Pareja::getId))
                .map(DisponibilidadParejaService::aResponse)
                .toList();
    }

    @Transactional
    public DisponibilidadParejaResponse actualizar(Long torneoId, Long parejaId, DisponibilidadParejaRequest request) {
        Pareja pareja = parejaRepository.findById(parejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pareja", parejaId));
        if (!pareja.getTorneo().getId().equals(torneoId)) {
            throw new ResourceNotFoundException("Pareja", parejaId);
        }
        pareja.getRestricciones().clear();
        pareja.getRestricciones().addAll(validar(request.getRestricciones()));
        pareja.setRestriccionesNotas(limpiar(request.getNotas()));
        parejaRepository.save(pareja);
        return aResponse(pareja);
    }

    /**
     * Convierte y valida lo que llega del formulario. Lo usa también la inscripción
     * pública: un horario mal cargado se rechaza ahí, cuando la persona todavía puede
     * corregirlo, y no días después cuando se arma el fixture.
     */
    public static List<RestriccionHoraria> validar(List<RestriccionHorariaDto> entrada) {
        if (entrada == null) {
            return new ArrayList<>();
        }
        List<RestriccionHoraria> restricciones = new ArrayList<>();
        for (RestriccionHorariaDto dto : entrada) {
            if ((dto.getFecha() == null) == (dto.getDiaSemana() == null)) {
                throw new EstadoInvalidoException("Cada horario tiene que ser de un día del torneo o de un día de la semana.");
            }
            if (!dto.getHoraHasta().isAfter(dto.getHoraDesde())) {
                throw new EstadoInvalidoException("Un horario termina antes de empezar (" + dto.getHoraDesde() + " a "
                        + dto.getHoraHasta() + "). Si es hasta medianoche, poné 23:59.");
            }
            restricciones.add(RestriccionHoraria.builder()
                    .fecha(dto.getFecha())
                    .diaSemana(dto.getDiaSemana())
                    .horaDesde(dto.getHoraDesde())
                    .horaHasta(dto.getHoraHasta())
                    .build());
        }
        return restricciones;
    }

    public static String limpiar(String notas) {
        return notas == null || notas.isBlank() ? null : notas.trim();
    }

    /** "sábado 7/11 de 9:00 a 14:00" o "los martes de 18:00 a 23:59": para mensajes y listados. */
    public static String describir(RestriccionHoraria restriccion) {
        String dia = restriccion.getFecha() != null
                ? DIAS[restriccion.getFecha().getDayOfWeek().getValue()] + " " + restriccion.getFecha().getDayOfMonth()
                        + "/" + restriccion.getFecha().getMonthValue()
                : "los " + DIAS[restriccion.getDiaSemana()] + (restriccion.getDiaSemana() >= 6 ? "s" : "");
        return dia + " de " + restriccion.getHoraDesde() + " a " + restriccion.getHoraHasta();
    }

    private static DisponibilidadParejaResponse aResponse(Pareja pareja) {
        return DisponibilidadParejaResponse.builder()
                .parejaId(pareja.getId())
                .categoriaNombre(pareja.getCategoria() != null ? pareja.getCategoria().getNombre() : null)
                .jugador1Nombre(nombre(pareja.getJugador1()))
                .jugador2Nombre(nombre(pareja.getJugador2()))
                .restricciones(pareja.getRestricciones().stream()
                        .map(r -> RestriccionHorariaDto.builder()
                                .fecha(r.getFecha())
                                .diaSemana(r.getDiaSemana())
                                .horaDesde(r.getHoraDesde())
                                .horaHasta(r.getHoraHasta())
                                .build())
                        .toList())
                .notas(pareja.getRestriccionesNotas())
                .build();
    }

    private static String nombre(Jugador jugador) {
        return jugador == null ? null : (jugador.getNombre() + " " + jugador.getApellido()).trim();
    }
}
