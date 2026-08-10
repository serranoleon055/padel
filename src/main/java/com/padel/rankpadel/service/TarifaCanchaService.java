package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.TarifaCanchaRequest;
import com.padel.rankpadel.dto.response.TarifaCanchaResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.TarifaCancha;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.TarifaCanchaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tarifas por franja horaria. Sin franjas cargadas, todo sigue valiendo
 * {@code Cancha.precioPorHora}.
 */
@Service
@RequiredArgsConstructor
public class TarifaCanchaService {

    private static final int MINUTOS_DEL_DIA = 24 * 60;

    private final TarifaCanchaRepository tarifaCanchaRepository;
    private final CanchaRepository canchaRepository;

    @Transactional(readOnly = true)
    public List<TarifaCanchaResponse> listar(Long canchaId) {
        return tarifaCanchaRepository.findByCanchaIdOrderByHoraDesdeAsc(canchaId).stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Precio por hora vigente para ese día y horario, o null si ninguna franja lo cubre
     * (en cuyo caso manda la tarifa por defecto de la cancha).
     */
    @Transactional(readOnly = true)
    public BigDecimal precioPorHora(Long canchaId, LocalDate fecha, LocalTime hora) {
        return tarifaCanchaRepository.findByCanchaIdAndActivoTrue(canchaId).stream()
                .filter(tarifa -> tarifa.cubre(fecha, hora))
                .map(TarifaCancha::getPrecioPorHora)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public TarifaCanchaResponse crear(TarifaCanchaRequest request) {
        TarifaCancha tarifa = TarifaCancha.builder()
                .cancha(cancha(request.getCanchaId()))
                .nombre(request.getNombre().trim())
                .diasSemana(request.getDiasSemana())
                .horaDesde(request.getHoraDesde())
                .horaHasta(request.getHoraHasta())
                .precioPorHora(request.getPrecioPorHora())
                .activo(true)
                .build();
        validar(tarifa, null);
        tarifaCanchaRepository.save(tarifa);
        return aResponse(tarifa);
    }

    @Transactional
    public TarifaCanchaResponse actualizar(Long id, TarifaCanchaRequest request) {
        TarifaCancha tarifa = buscar(id);
        tarifa.setCancha(cancha(request.getCanchaId()));
        tarifa.setNombre(request.getNombre().trim());
        tarifa.setDiasSemana(request.getDiasSemana());
        tarifa.setHoraDesde(request.getHoraDesde());
        tarifa.setHoraHasta(request.getHoraHasta());
        tarifa.setPrecioPorHora(request.getPrecioPorHora());
        validar(tarifa, id);
        tarifaCanchaRepository.save(tarifa);
        return aResponse(tarifa);
    }

    @Transactional
    public void eliminar(Long id) {
        tarifaCanchaRepository.delete(buscar(id));
    }

    /**
     * Dos franjas superpuestas dejarían el precio del turno librado al orden de la
     * consulta: se rechazan al cargarlas en vez de resolver la ambigüedad en silencio.
     */
    private void validar(TarifaCancha tarifa, Long idExcluido) {
        if (tarifa.getHoraDesde().equals(tarifa.getHoraHasta())) {
            throw new EstadoInvalidoException(
                    "La franja tiene que tener una duración. Para cubrir todo el día usá 00:00 a 23:59.");
        }
        for (TarifaCancha otra : tarifaCanchaRepository.findByCanchaIdAndActivoTrue(tarifa.getCancha().getId())) {
            if (idExcluido != null && idExcluido.equals(otra.getId())) {
                continue;
            }
            if (compartenDia(tarifa, otra) && seSolapan(tarifa, otra)) {
                throw new EstadoInvalidoException(
                        "Se superpone con la franja \"" + otra.getNombre() + "\"");
            }
        }
    }

    private boolean compartenDia(TarifaCancha a, TarifaCancha b) {
        Set<String> diasA = new HashSet<>(Arrays.asList(a.getDiasSemana().split(",")));
        for (String dia : b.getDiasSemana().split(",")) {
            if (diasA.contains(dia.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean seSolapan(TarifaCancha a, TarifaCancha b) {
        for (int[] rangoA : intervalos(a)) {
            for (int[] rangoB : intervalos(b)) {
                if (rangoA[0] < rangoB[1] && rangoB[0] < rangoA[1]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Una franja que cruza medianoche son dos tramos sobre la línea de minutos del día. */
    private List<int[]> intervalos(TarifaCancha tarifa) {
        int desde = tarifa.getHoraDesde().toSecondOfDay() / 60;
        int hasta = tarifa.getHoraHasta().toSecondOfDay() / 60;
        List<int[]> tramos = new ArrayList<>();
        if (desde < hasta) {
            tramos.add(new int[] { desde, hasta });
        } else {
            tramos.add(new int[] { desde, MINUTOS_DEL_DIA });
            tramos.add(new int[] { 0, hasta });
        }
        return tramos;
    }

    private Cancha cancha(Long canchaId) {
        return canchaRepository.findById(canchaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", canchaId));
    }

    private TarifaCancha buscar(Long id) {
        return tarifaCanchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarifa", id));
    }

    private TarifaCanchaResponse aResponse(TarifaCancha tarifa) {
        Cancha cancha = tarifa.getCancha();
        return TarifaCanchaResponse.builder()
                .id(tarifa.getId())
                .canchaId(cancha != null ? cancha.getId() : null)
                .canchaNombre(cancha != null ? cancha.getNombre() : null)
                .nombre(tarifa.getNombre())
                .diasSemana(tarifa.getDiasSemana())
                .horaDesde(tarifa.getHoraDesde())
                .horaHasta(tarifa.getHoraHasta())
                .precioPorHora(tarifa.getPrecioPorHora())
                .activo(tarifa.isActivo())
                .build();
    }
}
