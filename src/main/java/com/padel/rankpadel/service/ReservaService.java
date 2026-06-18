package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.LoteReservaRequest;
import com.padel.rankpadel.dto.request.SolicitudReservaRequest;
import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.ReservaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private static final int EXPIRACION_MINUTOS = 90;
    private static final long MAX_PENDIENTES_POR_TELEFONO = 3;

    private final ReservaRepository reservaRepository;
    private final CanchaRepository canchaRepository;
    private final DisponibilidadCanchaService disponibilidadCanchaService;

    @Transactional
    public ReservaResponse solicitar(SolicitudReservaRequest request) {
        Cancha cancha = canchaParaReservar(request.getCanchaId());
        String telefono = request.getClienteTelefono().trim();
        validarTopePendientes(telefono, 1);
        Reserva reserva = crearReserva(cancha, request.getFecha(), request.getHoraInicio(),
                request.getClienteNombre(), telefono, null, EXPIRACION_MINUTOS);
        return aResponse(reserva);
    }

    @Transactional
    public List<ReservaResponse> solicitarLote(LoteReservaRequest request) {
        Cancha cancha = canchaParaReservar(request.getCanchaId());
        String telefono = request.getClienteTelefono().trim();
        List<LocalTime> horarios = request.getHorarios().stream().distinct().sorted().toList();
        validarTopePendientes(telefono, horarios.size());

        List<ReservaResponse> creadas = new ArrayList<>();
        for (LocalTime horaInicio : horarios) {
            Reserva reserva = crearReserva(cancha, request.getFecha(), horaInicio,
                    request.getClienteNombre(), telefono, null, EXPIRACION_MINUTOS);
            creadas.add(aResponse(reserva));
        }
        return creadas;
    }

    @Transactional
    public List<Reserva> crearReservasParaPago(LoteReservaRequest request, Pago pago, int expiracionMinutos) {
        Cancha cancha = canchaParaReservar(request.getCanchaId());
        String telefono = request.getClienteTelefono().trim();
        List<LocalTime> horarios = request.getHorarios().stream().distinct().sorted().toList();
        validarTopePendientes(telefono, horarios.size());

        List<Reserva> creadas = new ArrayList<>();
        for (LocalTime horaInicio : horarios) {
            creadas.add(crearReserva(cancha, request.getFecha(), horaInicio,
                    request.getClienteNombre(), telefono, pago, expiracionMinutos));
        }
        return creadas;
    }

    private Cancha canchaParaReservar(Long canchaId) {
        Cancha cancha = canchaRepository.findById(canchaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", canchaId));
        if (!cancha.isActivo()) {
            throw new EstadoInvalidoException("La cancha no está disponible para reservas");
        }
        return cancha;
    }

    private void validarTopePendientes(String telefono, int cantidadNueva) {
        long pendientes = reservaRepository.countByClienteTelefonoAndEstado(telefono, EstadoReserva.PENDIENTE);
        if (pendientes + cantidadNueva > MAX_PENDIENTES_POR_TELEFONO) {
            throw new EstadoInvalidoException(
                    "Con ese teléfono podés tener hasta " + MAX_PENDIENTES_POR_TELEFONO
                            + " turnos pendientes de confirmación. Esperá a que el club confirme los actuales.");
        }
    }

    private Reserva crearReserva(Cancha cancha, LocalDate fecha, LocalTime horaInicio,
            String clienteNombre, String telefono, Pago pago, int expiracionMinutos) {
        LocalTime horaFin = horaInicio.plusMinutes(disponibilidadCanchaService.duracionSlot(cancha.getId()));

        if (disponibilidadCanchaService.inicioReal(cancha.getId(), fecha, horaInicio).isBefore(LocalDateTime.now())) {
            throw new EstadoInvalidoException("No se puede reservar un horario que ya pasó");
        }
        if (!disponibilidadCanchaService.rangoLibre(cancha.getId(), fecha, horaInicio, horaFin)) {
            throw new EstadoInvalidoException("El horario de las " + horaInicio + " ya no está disponible");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Reserva reserva = Reserva.builder()
                .cancha(cancha)
                .fecha(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaFin)
                .estado(EstadoReserva.PENDIENTE)
                .clienteNombre(clienteNombre.trim())
                .clienteTelefono(telefono)
                .codigo(generarCodigo())
                .creadoEn(ahora)
                .expiraEn(ahora.plusMinutes(expiracionMinutos))
                .claveSlot(claveSlot(cancha.getId(), fecha, horaInicio))
                .pago(pago)
                .build();

        try {
            reservaRepository.save(reserva);
        } catch (DataIntegrityViolationException e) {
            throw new EstadoInvalidoException("El horario de las " + horaInicio + " acaba de ser reservado por otra persona");
        }

        return reserva;
    }

    @Transactional
    public ReservaResponse confirmar(Long id) {
        Reserva reserva = pendiente(id);
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reserva.setConfirmadoEn(LocalDateTime.now());
        reservaRepository.save(reserva);
        return aResponse(reserva);
    }

    @Transactional
    public ReservaResponse rechazar(Long id) {
        Reserva reserva = pendiente(id);
        liberar(reserva, EstadoReserva.RECHAZADA);
        return aResponse(reserva);
    }

    @Transactional
    public ReservaResponse cancelar(Long id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", id));
        if (reserva.getEstado() == EstadoReserva.CANCELADA
                || reserva.getEstado() == EstadoReserva.RECHAZADA
                || reserva.getEstado() == EstadoReserva.EXPIRADA) {
            throw new EstadoInvalidoException("La reserva ya no está activa");
        }
        liberar(reserva, EstadoReserva.CANCELADA);
        return aResponse(reserva);
    }

    @Transactional
    public int expirarPendientesVencidas() {
        List<Reserva> vencidas = reservaRepository
                .findByEstadoAndExpiraEnBefore(EstadoReserva.PENDIENTE, LocalDateTime.now());
        for (Reserva reserva : vencidas) {
            liberar(reserva, EstadoReserva.EXPIRADA);
        }
        return vencidas.size();
    }

    @Transactional(readOnly = true)
    public List<ReservaResponse> listarPorFecha(Long canchaId, LocalDate fecha) {
        return reservaRepository.findByCanchaIdAndFecha(canchaId, fecha).stream()
                .map(this::aResponse)
                .toList();
    }

    private Reserva pendiente(Long id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", id));
        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new EstadoInvalidoException("Solo se puede operar sobre una reserva PENDIENTE");
        }
        return reserva;
    }

    private void liberar(Reserva reserva, EstadoReserva nuevoEstado) {
        reserva.setEstado(nuevoEstado);
        reserva.setClaveSlot(null);
        reservaRepository.save(reserva);
    }

    private String claveSlot(Long canchaId, LocalDate fecha, LocalTime horaInicio) {
        return canchaId + "|" + fecha + "|" + horaInicio;
    }

    private String generarCodigo() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private ReservaResponse aResponse(Reserva reserva) {
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
                .build();
    }
}
