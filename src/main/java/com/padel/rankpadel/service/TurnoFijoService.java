package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.TurnoFijoRequest;
import com.padel.rankpadel.dto.response.GeneracionTurnosFijosResponse;
import com.padel.rankpadel.dto.response.TurnoFijoResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.TurnoFijo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.TurnoFijoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turnos fijos (abonos). El club los define una vez y el sistema genera las reservas
 * concretas con anticipación, para que ocupen la grilla como cualquier otro turno.
 */
@Service
@RequiredArgsConstructor
public class TurnoFijoService {

    private static final Logger log = LoggerFactory.getLogger(TurnoFijoService.class);

    private final TurnoFijoRepository turnoFijoRepository;
    private final CanchaRepository canchaRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;
    private final DisponibilidadCanchaService disponibilidadCanchaService;
    private final NotificacionService notificacionService;

    @Value("${app.turnos-fijos.semanas-adelante:6}")
    private int semanasAdelante;

    @Transactional(readOnly = true)
    public List<TurnoFijoResponse> listar(Long lugarId, Long canchaId) {
        return turnoFijoRepository.buscar(lugarId, canchaId).stream()
                .map(this::aResponse)
                .toList();
    }

    @Transactional
    public TurnoFijoResponse crear(TurnoFijoRequest request) {
        TurnoFijo turnoFijo = TurnoFijo.builder()
                .cancha(canchaValida(request.getCanchaId()))
                .diaSemana(request.getDiaSemana())
                .horaInicio(request.getHoraInicio())
                .slots(request.getSlots() != null ? request.getSlots() : 1)
                .clienteNombre(request.getClienteNombre().trim())
                .clienteTelefono(request.getClienteTelefono().trim())
                .precioPactado(request.getPrecioPactado())
                .vigenteDesde(request.getVigenteDesde())
                .vigenteHasta(request.getVigenteHasta())
                .activo(true)
                .notas(request.getNotas())
                .creadoEn(LocalDateTime.now())
                .build();
        validarVigencia(turnoFijo);
        turnoFijoRepository.save(turnoFijo);
        return aResponse(turnoFijo);
    }

    @Transactional
    public TurnoFijoResponse actualizar(Long id, TurnoFijoRequest request) {
        TurnoFijo turnoFijo = buscar(id);
        boolean cambiaElHorario = turnoFijo.getDiaSemana() != request.getDiaSemana()
                || !turnoFijo.getHoraInicio().equals(request.getHoraInicio())
                || !turnoFijo.getCancha().getId().equals(request.getCanchaId());

        turnoFijo.setCancha(canchaValida(request.getCanchaId()));
        turnoFijo.setDiaSemana(request.getDiaSemana());
        turnoFijo.setHoraInicio(request.getHoraInicio());
        turnoFijo.setSlots(request.getSlots() != null ? request.getSlots() : 1);
        turnoFijo.setClienteNombre(request.getClienteNombre().trim());
        turnoFijo.setClienteTelefono(request.getClienteTelefono().trim());
        turnoFijo.setPrecioPactado(request.getPrecioPactado());
        turnoFijo.setVigenteDesde(request.getVigenteDesde());
        turnoFijo.setVigenteHasta(request.getVigenteHasta());
        turnoFijo.setNotas(request.getNotas());
        validarVigencia(turnoFijo);
        turnoFijoRepository.save(turnoFijo);

        // Si cambió cancha, día u hora, las reservas ya generadas quedaron en el horario
        // viejo: se dan de baja y se regeneran en el nuevo.
        if (cambiaElHorario) {
            reservaService.cancelarFuturasDeTurnoFijo(turnoFijo.getId(), LocalDate.now());
        }
        return aResponse(turnoFijo);
    }

    /** Baja del abono: se desactiva y se liberan los turnos futuros ya generados. */
    @Transactional
    public void darDeBaja(Long id) {
        TurnoFijo turnoFijo = buscar(id);
        turnoFijo.setActivo(false);
        turnoFijoRepository.save(turnoFijo);
        int liberados = reservaService.cancelarFuturasDeTurnoFijo(id, LocalDate.now());
        log.info("Turno fijo {} dado de baja: {} turnos futuros liberados", id, liberados);
    }

    /** Genera las reservas de todos los turnos fijos activos. Idempotente. */
    public GeneracionTurnosFijosResponse generarTodos() {
        List<GeneracionTurnosFijosResponse.Conflicto> conflictos = new ArrayList<>();
        int generadas = 0;
        for (TurnoFijo turnoFijo : turnoFijoRepository.findActivosParaGenerar()) {
            generadas += generar(turnoFijo, conflictos);
        }
        if (!conflictos.isEmpty()) {
            notificacionService.avisarConflictosTurnosFijos(conflictos);
        }
        return GeneracionTurnosFijosResponse.builder()
                .generadas(generadas)
                .conflictos(conflictos)
                .build();
    }

    public GeneracionTurnosFijosResponse generarUno(Long id) {
        TurnoFijo turnoFijo = turnoFijoRepository.findByIdConCancha(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno fijo", id));
        List<GeneracionTurnosFijosResponse.Conflicto> conflictos = new ArrayList<>();
        int generadas = turnoFijo.isActivo() ? generar(turnoFijo, conflictos) : 0;
        if (!conflictos.isEmpty()) {
            notificacionService.avisarConflictosTurnosFijos(conflictos);
        }
        return GeneracionTurnosFijosResponse.builder()
                .generadas(generadas)
                .conflictos(conflictos)
                .build();
    }

    private int generar(TurnoFijo turnoFijo, List<GeneracionTurnosFijosResponse.Conflicto> conflictos) {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = turnoFijo.getVigenteDesde().isAfter(hoy) ? turnoFijo.getVigenteDesde() : hoy;
        LocalDate hasta = hoy.plusWeeks(semanasAdelante);
        if (turnoFijo.getVigenteHasta() != null && turnoFijo.getVigenteHasta().isBefore(hasta)) {
            hasta = turnoFijo.getVigenteHasta();
        }
        if (desde.isAfter(hasta)) {
            return 0;
        }

        // Las fechas ya generadas se saltean en cualquier estado: si el club dio de baja
        // la semana que el cliente avisó que no venía, no hay que volver a crearla.
        Set<LocalDate> yaGeneradas = new HashSet<>(reservaRepository.findFechasGeneradas(turnoFijo.getId(), desde));
        BigDecimal precioPorSlot = precioPorSlot(turnoFijo);
        int duracion = disponibilidadCanchaService.duracionSlot(turnoFijo.getCancha().getId());

        int generadas = 0;
        LocalDate fecha = desde;
        while (!fecha.isAfter(hasta)) {
            if (fecha.getDayOfWeek().getValue() != turnoFijo.getDiaSemana() || yaGeneradas.contains(fecha)) {
                fecha = fecha.plusDays(1);
                continue;
            }
            generadas += generarFecha(turnoFijo, fecha, duracion, precioPorSlot, conflictos);
            fecha = fecha.plusDays(1);
        }
        return generadas;
    }

    private int generarFecha(TurnoFijo turnoFijo, LocalDate fecha, int duracion, BigDecimal precioPorSlot,
            List<GeneracionTurnosFijosResponse.Conflicto> conflictos) {
        int generadas = 0;
        for (int i = 0; i < turnoFijo.getSlots(); i++) {
            LocalTime horaInicio = turnoFijo.getHoraInicio().plusMinutes((long) duracion * i);
            if (reservaService.crearParaTurnoFijo(turnoFijo, fecha, horaInicio, precioPorSlot).isPresent()) {
                generadas++;
            } else {
                conflictos.add(GeneracionTurnosFijosResponse.Conflicto.builder()
                        .turnoFijoId(turnoFijo.getId())
                        .clienteNombre(turnoFijo.getClienteNombre())
                        .canchaNombre(turnoFijo.getCancha().getNombre())
                        .fecha(fecha)
                        .horaInicio(horaInicio.toString())
                        .motivo("El horario ya estaba ocupado")
                        .build());
                log.warn("Turno fijo {} ({}): {} {} ya estaba ocupado, no se generó",
                        turnoFijo.getId(), turnoFijo.getClienteNombre(), fecha, horaInicio);
            }
        }
        return generadas;
    }

    /** El precio pactado es del turno completo; en la reserva se guarda por horario. */
    private BigDecimal precioPorSlot(TurnoFijo turnoFijo) {
        if (turnoFijo.getPrecioPactado() == null) {
            return null;
        }
        int slots = Math.max(turnoFijo.getSlots(), 1);
        return turnoFijo.getPrecioPactado().divide(BigDecimal.valueOf(slots), 2, RoundingMode.HALF_UP);
    }

    private void validarVigencia(TurnoFijo turnoFijo) {
        if (turnoFijo.getVigenteHasta() != null
                && turnoFijo.getVigenteHasta().isBefore(turnoFijo.getVigenteDesde())) {
            throw new EstadoInvalidoException("La fecha de fin no puede ser anterior a la de inicio");
        }
    }

    private Cancha canchaValida(Long canchaId) {
        Cancha cancha = canchaRepository.findById(canchaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", canchaId));
        if (!cancha.isActivo()) {
            throw new EstadoInvalidoException("La cancha no está disponible");
        }
        return cancha;
    }

    private TurnoFijo buscar(Long id) {
        return turnoFijoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno fijo", id));
    }

    private TurnoFijoResponse aResponse(TurnoFijo turnoFijo) {
        Cancha cancha = turnoFijo.getCancha();
        int duracion = disponibilidadCanchaService.duracionSlot(cancha.getId());
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
                .horaFin(turnoFijo.getHoraInicio().plusMinutes((long) duracion * turnoFijo.getSlots()))
                .slots(turnoFijo.getSlots())
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
