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

import com.padel.rankpadel.dto.request.PromocionCanchaRequest;
import com.padel.rankpadel.dto.response.PromocionCanchaResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.PromocionCancha;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.PromocionCanchaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Promociones: precios especiales con nombre, días, horario y vigencia. Sin ninguna
 * cargada, todo sigue valiendo {@code Cancha.precioPorHora}.
 */
@Service
@RequiredArgsConstructor
public class PromocionCanchaService {

    private static final int MINUTOS_DEL_DIA = 24 * 60;

    private final PromocionCanchaRepository promocionCanchaRepository;
    private final CanchaRepository canchaRepository;

    @Transactional(readOnly = true)
    public List<PromocionCanchaResponse> listar(Long canchaId) {
        return promocionCanchaRepository.findByCanchaIdOrderByHoraDesdeAsc(canchaId).stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Precio por hora para ese día y horario según las promociones vigentes, o null si
     * ninguna lo cubre (en cuyo caso manda la tarifa por defecto de la cancha).
     */
    @Transactional(readOnly = true)
    public BigDecimal precioPorHora(Long canchaId, LocalDate fecha, LocalTime hora) {
        return promocionCanchaRepository.findByCanchaIdAndActivoTrue(canchaId).stream()
                .filter(promocion -> promocion.cubre(fecha, hora))
                .map(PromocionCancha::getPrecioPorHora)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public PromocionCanchaResponse crear(PromocionCanchaRequest request) {
        PromocionCancha promocion = PromocionCancha.builder()
                .cancha(cancha(request.getCanchaId()))
                .nombre(request.getNombre().trim())
                .diasSemana(request.getDiasSemana())
                .horaDesde(request.getHoraDesde())
                .horaHasta(request.getHoraHasta())
                .precioPorHora(request.getPrecioPorHora())
                .vigenteDesde(request.getVigenteDesde())
                .vigenteHasta(request.getVigenteHasta())
                .activo(true)
                .build();
        validar(promocion, null);
        promocionCanchaRepository.save(promocion);
        return aResponse(promocion);
    }

    @Transactional
    public PromocionCanchaResponse actualizar(Long id, PromocionCanchaRequest request) {
        PromocionCancha promocion = buscar(id);
        promocion.setCancha(cancha(request.getCanchaId()));
        promocion.setNombre(request.getNombre().trim());
        promocion.setDiasSemana(request.getDiasSemana());
        promocion.setHoraDesde(request.getHoraDesde());
        promocion.setHoraHasta(request.getHoraHasta());
        promocion.setPrecioPorHora(request.getPrecioPorHora());
        promocion.setVigenteDesde(request.getVigenteDesde());
        promocion.setVigenteHasta(request.getVigenteHasta());
        validar(promocion, id);
        promocionCanchaRepository.save(promocion);
        return aResponse(promocion);
    }

    @Transactional
    public void eliminar(Long id) {
        promocionCanchaRepository.delete(buscar(id));
    }

    /**
     * Dos promociones superpuestas dejarían el precio del turno librado al orden de la
     * consulta: se rechazan al cargarlas en vez de resolver la ambigüedad en silencio.
     */
    private void validar(PromocionCancha promocion, Long idExcluido) {
        if (promocion.getHoraDesde().equals(promocion.getHoraHasta())) {
            throw new EstadoInvalidoException(
                    "La promoción tiene que durar algo. Para cubrir todo el día usá 00:00 a 23:59.");
        }
        if (promocion.getVigenteDesde() != null && promocion.getVigenteHasta() != null
                && promocion.getVigenteHasta().isBefore(promocion.getVigenteDesde())) {
            throw new EstadoInvalidoException("La fecha de fin no puede ser anterior a la de inicio");
        }
        for (PromocionCancha otraPromo : promocionCanchaRepository.findByCanchaIdAndActivoTrue(promocion.getCancha().getId())) {
            if (idExcluido != null && idExcluido.equals(otraPromo.getId())) {
                continue;
            }
            // Dos promociones pueden compartir horario si no corren al mismo tiempo: es
            // justamente lo que pasa cuando una reemplaza a la anterior.
            if (vigenciasSeCruzan(promocion, otraPromo)
                    && compartenDia(promocion, otraPromo) && seSolapan(promocion, otraPromo)) {
                throw new EstadoInvalidoException(
                        "Se superpone con la promoción \"" + otraPromo.getNombre() + "\"");
            }
        }
    }

    private boolean vigenciasSeCruzan(PromocionCancha una, PromocionCancha otra) {
        boolean unaTerminaAntes = una.getVigenteHasta() != null && otra.getVigenteDesde() != null
                && una.getVigenteHasta().isBefore(otra.getVigenteDesde());
        boolean otraTerminaAntes = otra.getVigenteHasta() != null && una.getVigenteDesde() != null
                && otra.getVigenteHasta().isBefore(una.getVigenteDesde());
        return !unaTerminaAntes && !otraTerminaAntes;
    }

    private boolean compartenDia(PromocionCancha a, PromocionCancha b) {
        Set<String> diasA = new HashSet<>(Arrays.asList(a.getDiasSemana().split(",")));
        for (String dia : b.getDiasSemana().split(",")) {
            if (diasA.contains(dia.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean seSolapan(PromocionCancha a, PromocionCancha b) {
        for (int[] rangoA : intervalos(a)) {
            for (int[] rangoB : intervalos(b)) {
                if (rangoA[0] < rangoB[1] && rangoB[0] < rangoA[1]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Una promoción que cruza medianoche son dos tramos sobre la línea de minutos del día. */
    private List<int[]> intervalos(PromocionCancha promocion) {
        int desde = promocion.getHoraDesde().toSecondOfDay() / 60;
        int hasta = promocion.getHoraHasta().toSecondOfDay() / 60;
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

    private PromocionCancha buscar(Long id) {
        return promocionCanchaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promoción", id));
    }

    private PromocionCanchaResponse aResponse(PromocionCancha promocion) {
        Cancha cancha = promocion.getCancha();
        return PromocionCanchaResponse.builder()
                .id(promocion.getId())
                .canchaId(cancha != null ? cancha.getId() : null)
                .canchaNombre(cancha != null ? cancha.getNombre() : null)
                .nombre(promocion.getNombre())
                .diasSemana(promocion.getDiasSemana())
                .horaDesde(promocion.getHoraDesde())
                .horaHasta(promocion.getHoraHasta())
                .precioPorHora(promocion.getPrecioPorHora())
                .vigenteDesde(promocion.getVigenteDesde())
                .vigenteHasta(promocion.getVigenteHasta())
                .vigente(promocion.isActivo() && promocion.vigenteEn(LocalDate.now()))
                .activo(promocion.isActivo())
                .build();
    }
}
