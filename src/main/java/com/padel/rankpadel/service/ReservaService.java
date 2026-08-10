package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.LoteReservaRequest;
import com.padel.rankpadel.dto.request.SolicitudReservaRequest;
import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.TurnoFijo;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.ReservaMapper;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.CobroRepository;
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
    private final NotificacionService notificacionService;
    private final ClienteService clienteService;
    private final ReservaMapper reservaMapper;
    private final CobroRepository cobroRepository;

    @Transactional
    public ReservaResponse solicitar(SolicitudReservaRequest request) {
        Cancha cancha = canchaParaReservar(request.getCanchaId());
        String telefono = request.getClienteTelefono().trim();
        validarTopePendientes(telefono, 1);
        Reserva reserva = crearReserva(cancha, request.getFecha(), request.getHoraInicio(),
                request.getClienteNombre(), telefono, null, EXPIRACION_MINUTOS);
        notificacionService.avisarNuevaSolicitudReserva(reserva);
        return aResponse(reserva);
    }

    @Transactional
    public List<ReservaResponse> solicitarLote(LoteReservaRequest request) {
        Cancha cancha = canchaParaReservar(request.getCanchaId());
        String telefono = request.getClienteTelefono().trim();
        List<LocalTime> horarios = request.getHorarios().stream().distinct().sorted().toList();
        validarTopePendientes(telefono, horarios.size());

        List<ReservaResponse> creadas = new ArrayList<>();
        Reserva primera = null;
        for (LocalTime horaInicio : horarios) {
            Reserva reserva = crearReserva(cancha, request.getFecha(), horaInicio,
                    request.getClienteNombre(), telefono, null, EXPIRACION_MINUTOS);
            if (primera == null) {
                primera = reserva;
            }
            creadas.add(aResponse(reserva));
        }
        // Un pedido de 2 h son varios slots pero una sola solicitud: un solo aviso.
        notificacionService.avisarNuevaSolicitudReserva(primera);
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

    /**
     * Crea una reserva de un turno fijo, ya CONFIRMADA (el cliente es del club, no hay
     * nada que aprobar) y sin expiración.
     *
     * <p>Va en su propia transacción: si un horario suelto se coló en ese slot, se
     * descarta esa fecha sola y la generación del resto sigue. Devuelve vacío cuando el
     * horario está ocupado, para que el club se entere en vez de perderse el turno.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Reserva> crearParaTurnoFijo(TurnoFijo turnoFijo, LocalDate fecha, LocalTime horaInicio,
            BigDecimal precio) {
        Cancha cancha = turnoFijo.getCancha();
        LocalTime horaFin = horaInicio.plusMinutes(disponibilidadCanchaService.duracionSlot(cancha.getId()));

        if (!disponibilidadCanchaService.rangoLibre(cancha.getId(), fecha, horaInicio, horaFin)) {
            return Optional.empty();
        }

        Reserva reserva = Reserva.builder()
                .cancha(cancha)
                .fecha(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaFin)
                .precioAplicado(precio != null ? precio : disponibilidadCanchaService.precioSlot(cancha))
                .estado(EstadoReserva.CONFIRMADA)
                .clienteNombre(turnoFijo.getClienteNombre())
                .clienteTelefono(turnoFijo.getClienteTelefono())
                .codigo(generarCodigo())
                .creadoEn(LocalDateTime.now())
                .confirmadoEn(LocalDateTime.now())
                .claveSlot(claveSlot(cancha.getId(), fecha, horaInicio))
                .turnoFijo(turnoFijo)
                .cliente(clienteService.buscarOCrear(turnoFijo.getClienteNombre(), turnoFijo.getClienteTelefono()))
                .build();

        try {
            reservaRepository.save(reserva);
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
        return Optional.of(reserva);
    }

    /** Da de baja las reservas futuras de un turno fijo que se dio de baja o se acortó. */
    @Transactional
    public int cancelarFuturasDeTurnoFijo(Long turnoFijoId, LocalDate desde) {
        List<Reserva> futuras = reservaRepository.findByTurnoFijoIdAndFechaGreaterThanEqual(turnoFijoId, desde);
        int canceladas = 0;
        for (Reserva reserva : futuras) {
            if (reserva.getEstado() == EstadoReserva.PENDIENTE || reserva.getEstado() == EstadoReserva.CONFIRMADA) {
                liberar(reserva, EstadoReserva.CANCELADA);
                canceladas++;
            }
        }
        return canceladas;
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

        LocalDateTime inicioReal = disponibilidadCanchaService.inicioReal(cancha.getId(), fecha, horaInicio);
        if (inicioReal.isBefore(LocalDateTime.now())) {
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
                .precioAplicado(disponibilidadCanchaService.precioSlot(cancha))
                .estado(EstadoReserva.PENDIENTE)
                .clienteNombre(clienteNombre.trim())
                .clienteTelefono(telefono)
                .codigo(generarCodigo())
                .creadoEn(ahora)
                .expiraEn(calcularExpiracion(cancha, ahora, inicioReal, pago, expiracionMinutos))
                .claveSlot(claveSlot(cancha.getId(), fecha, horaInicio))
                .pago(pago)
                .cliente(clienteService.buscarOCrear(clienteNombre, telefono))
                .build();

        try {
            reservaRepository.save(reserva);
        } catch (DataIntegrityViolationException e) {
            throw new EstadoInvalidoException("El horario de las " + horaInicio + " acaba de ser reservado por otra persona");
        }

        return reserva;
    }

    /**
     * Cuándo caduca una solicitud pendiente.
     *
     * <p>Con pago online, la ventana es la del checkout y corre desde el momento de crearla.
     * Sin pago, la solicitud la confirma una persona del club: contar 90 minutos de reloj
     * hacía que todo lo pedido de noche muriera antes de que el club abriera. Por eso el
     * plazo se cuenta desde que el club vuelve a estar abierto, y nunca se estira más allá
     * del comienzo del turno.
     */
    private LocalDateTime calcularExpiracion(Cancha cancha, LocalDateTime ahora,
            LocalDateTime inicioReal, Pago pago, int expiracionMinutos) {
        LocalDateTime expira = ahora.plusMinutes(expiracionMinutos);
        if (pago == null) {
            LocalDateTime desdeApertura = disponibilidadCanchaService
                    .proximaApertura(cancha.getId(), ahora)
                    .plusMinutes(expiracionMinutos);
            if (desdeApertura.isAfter(expira)) {
                expira = desdeApertura;
            }
        }
        return expira.isAfter(inicioReal) ? inicioReal : expira;
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

    /**
     * El cliente reservó y no vino. No se libera el horario ni se toca {@code claveSlot}:
     * la cancha estuvo bloqueada igual y el registro tiene que quedar.
     */
    @Transactional
    public ReservaResponse marcarNoShow(Long id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", id));
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA && reserva.getEstado() != EstadoReserva.FINALIZADA) {
            throw new EstadoInvalidoException("Solo se puede marcar como ausente un turno confirmado");
        }
        if (LocalDateTime.now().isBefore(inicioDe(reserva))) {
            throw new EstadoInvalidoException("El turno todavía no empezó");
        }
        reserva.setEstado(EstadoReserva.NO_SHOW);
        reservaRepository.save(reserva);
        return aResponse(reserva);
    }

    /** Deshace un ausente marcado por error. */
    @Transactional
    public ReservaResponse desmarcarNoShow(Long id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", id));
        if (reserva.getEstado() != EstadoReserva.NO_SHOW) {
            throw new EstadoInvalidoException("El turno no está marcado como ausente");
        }
        boolean yaTermino = LocalDateTime.now().isAfter(inicioDe(reserva)
                .plusMinutes(disponibilidadCanchaService.duracionSlot(
                        reserva.getCancha() != null ? reserva.getCancha().getId() : null)));
        reserva.setEstado(yaTermino ? EstadoReserva.FINALIZADA : EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);
        return aResponse(reserva);
    }

    private LocalDateTime inicioDe(Reserva reserva) {
        if (reserva.getCancha() == null) {
            return reserva.getFecha().atTime(reserva.getHoraInicio());
        }
        return disponibilidadCanchaService.inicioReal(
                reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());
    }

    @Transactional
    public boolean liberarPorPagoFallido(List<Reserva> reservas) {
        boolean liberadaAlguna = false;
        for (Reserva reserva : reservas) {
            Cancha cancha = reserva.getCancha();
            boolean exigeSenia = cancha == null || cancha.isSeniaObligatoria();
            if (reserva.getEstado() == EstadoReserva.PENDIENTE && exigeSenia) {
                liberar(reserva, EstadoReserva.CANCELADA);
                liberadaAlguna = true;
            }
        }
        return liberadaAlguna;
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

    @Transactional
    public int finalizarTurnosPasados() {
        List<Reserva> pasadas = reservaRepository.findConfirmadasFinalizadas(
                EstadoReserva.CONFIRMADA, LocalDate.now(), LocalTime.now());
        for (Reserva reserva : pasadas) {
            reserva.setEstado(EstadoReserva.FINALIZADA);
            reservaRepository.save(reserva);
        }
        return pasadas.size();
    }

    @Transactional(readOnly = true)
    public List<ReservaResponse> listarPorFecha(Long canchaId, LocalDate fecha) {
        List<Reserva> reservas = reservaRepository.findByCanchaIdAndFecha(canchaId, fecha);
        if (reservas.isEmpty()) {
            return List.of();
        }
        // Lo cobrado de todos los turnos del día en una sola consulta agrupada.
        Map<Long, BigDecimal> cobrado = new HashMap<>();
        for (CobroRepository.TotalPorReserva total : cobroRepository.totalesPorReserva(
                reservas.stream().map(Reserva::getId).toList())) {
            cobrado.put(total.getReservaId(), total.getTotal());
        }
        return reservas.stream()
                .map(reserva -> reservaMapper.aResponse(reserva, cobrado.get(reserva.getId())))
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
        return reservaMapper.aResponse(reserva);
    }
}
