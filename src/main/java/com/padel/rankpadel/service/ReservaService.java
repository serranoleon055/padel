package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.LoteReservaRequest;
import com.padel.rankpadel.dto.request.SolicitudReservaRequest;
import com.padel.rankpadel.dto.response.ReservaResponse;
import com.padel.rankpadel.dto.response.TurnoPublicoResponse;
import com.padel.rankpadel.util.MontosReserva;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.ReservaSlot;
import com.padel.rankpadel.entity.TurnoFijo;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.ReservaMapper;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.VentaRepository;
import com.padel.rankpadel.util.OrdenCanchas;
import com.padel.rankpadel.util.OrdenJornada;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private static final int EXPIRACION_MINUTOS = 90;
    private static final long MAX_PENDIENTES_POR_TELEFONO = 3;

    /** Turnos que el mostrador tiene que atender hoy: los que se juegan o ya se jugaron. */
    private static final List<EstadoReserva> EN_JUEGO = List.of(
            EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA,
            EstadoReserva.FINALIZADA, EstadoReserva.NO_SHOW);

    private final ReservaRepository reservaRepository;
    private final CanchaRepository canchaRepository;
    private final DisponibilidadCanchaService disponibilidadCanchaService;
    private final NotificacionService notificacionService;
    private final ClienteService clienteService;
    private final ReservaMapper reservaMapper;
    private final CobroRepository cobroRepository;
    private final VentaRepository ventaRepository;
    private final ConfiguracionSedeService configuracionSedeService;

    /**
     * Cuánto tiene que faltar para el turno para que el recordatorio sirva de algo. Menos
     * que esto y el aviso llega cuando la persona ya no puede avisar que no viene.
     */
    private static final int HORAS_MINIMAS_RECORDATORIO = 3;

    @org.springframework.beans.factory.annotation.Value("${app.front.base-url:http://localhost:5173}")
    private String frontBaseUrl;

    @Transactional
    public ReservaResponse solicitar(SolicitudReservaRequest request) {
        Cancha cancha = canchaParaReservar(request.getCanchaId());
        String telefono = request.getClienteTelefono().trim();
        validarTopePendientes(telefono, 1);
        Reserva reserva = crearReserva(cancha, request.getFecha(), request.getHoraInicio(),
                duracionPedida(cancha, request.getDuracionMin()),
                request.getClienteNombre(), telefono, request.getClienteEmail(), null, EXPIRACION_MINUTOS);
        avisarSiLoPidioUnJugador(reserva);
        return aResponse(reserva);
    }

    @Transactional
    public ReservaResponse solicitarLote(LoteReservaRequest request) {
        Cancha cancha = canchaParaReservar(request.getCanchaId());
        String telefono = request.getClienteTelefono().trim();
        validarTopePendientes(telefono, 1);
        Reserva reserva = crearReserva(cancha, request.getFecha(), request.getHoraInicio(),
                duracionPedida(cancha, request.getDuracionMin()),
                request.getClienteNombre(), telefono, request.getClienteEmail(), null, EXPIRACION_MINUTOS);
        avisarSiLoPidioUnJugador(reserva);
        return aResponse(reserva);
    }

    /**
     * El aviso por mail es para enterarse de lo que entra solo: un jugador pidiendo un
     * turno desde la web. Cuando el turno lo carga el mostrador —de frente al cliente o
     * por teléfono— el club ya está enterado, y mandarse un mail a sí mismo por cada
     * turno que tipea no es un aviso, es ruido que termina haciendo que no se lean.
     */
    private void avisarSiLoPidioUnJugador(Reserva reserva) {
        if (!loCargoElClub()) {
            notificacionService.avisarNuevaSolicitudReserva(reserva);
        }
        // El comprobante va siempre que haya mail, lo haya cargado el club o el jugador:
        // es lo único que le queda a la persona si cierra la pestaña.
        notificacionService.enviarComprobanteAlJugador(reserva, enlaceDelTurno(reserva));
    }

    /** Si el turno lo está cargando alguien del club desde el panel y no un jugador. */
    private boolean loCargoElClub() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @Transactional
    public Reserva crearReservaParaPago(LoteReservaRequest request, Pago pago, int expiracionMinutos) {
        Cancha cancha = canchaParaReservar(request.getCanchaId());
        String telefono = request.getClienteTelefono().trim();
        validarTopePendientes(telefono, 1);
        return crearReserva(cancha, request.getFecha(), request.getHoraInicio(),
                duracionPedida(cancha, request.getDuracionMin()),
                request.getClienteNombre(), telefono, request.getClienteEmail(), pago, expiracionMinutos);
    }

    /**
     * La duración tiene que ser una de las que el club vende. Aceptar cualquier número
     * dejaría entrar turnos de 45 minutos por la API que la agenda no sabe dibujar.
     */
    private int duracionPedida(Cancha cancha, Integer pedida) {
        List<Integer> ofrecidas = disponibilidadCanchaService.duracionesOfrecidas(cancha.getId());
        if (pedida == null) {
            return ofrecidas.get(0);
        }
        if (!ofrecidas.contains(pedida)) {
            throw new EstadoInvalidoException("Esta cancha se alquila por "
                    + ofrecidas.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))
                    + " minutos.");
        }
        return pedida;
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
        int duracion = turnoFijo.getDuracionMin();

        if (!disponibilidadCanchaService.rangoLibre(cancha.getId(), fecha, horaInicio, duracion)) {
            return Optional.empty();
        }

        Reserva reserva = Reserva.builder()
                .cancha(cancha)
                .fecha(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaInicio.plusMinutes(duracion))
                .duracionMin(duracion)
                .precioAplicado(precio != null ? precio
                        : disponibilidadCanchaService.precio(cancha, fecha, horaInicio, duracion))
                .estado(EstadoReserva.CONFIRMADA)
                .clienteNombre(turnoFijo.getClienteNombre())
                .clienteTelefono(turnoFijo.getClienteTelefono())
                .codigo(generarCodigo())
                .tokenPublico(UUID.randomUUID().toString())
                .creadoEn(LocalDateTime.now())
                .confirmadoEn(LocalDateTime.now())
                .turnoFijo(turnoFijo)
                .cliente(clienteService.buscarOCrear(turnoFijo.getClienteNombre(), turnoFijo.getClienteTelefono()))
                .build();
        tomarHorario(reserva);

        try {
            reservaRepository.saveAndFlush(reserva);
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

    /**
     * Tope de solicitudes sin confirmar por teléfono, para que nadie bloquee la agenda
     * desde la web.
     *
     * <p>No corre para el club: cuando el turno lo carga el mostrador, el cliente está
     * de frente o al teléfono y ya hay una persona decidiendo. Con el tope activo, a un
     * cliente que ya tenía tres turnos pedidos no se le podía agendar el cuarto desde el
     * panel, que es justo lo contrario de lo que la protección busca.
     */
    private void validarTopePendientes(String telefono, int cantidadNueva) {
        if (loCargoElClub()) {
            return;
        }
        long pendientes = reservaRepository.countByClienteTelefonoAndEstado(telefono, EstadoReserva.PENDIENTE);
        if (pendientes + cantidadNueva > MAX_PENDIENTES_POR_TELEFONO) {
            throw new EstadoInvalidoException(
                    "Con ese teléfono podés tener hasta " + MAX_PENDIENTES_POR_TELEFONO
                            + " turnos pendientes de confirmación. Esperá a que el club confirme los actuales.");
        }
    }

    private Reserva crearReserva(Cancha cancha, LocalDate fecha, LocalTime horaInicio, int duracionMin,
            String clienteNombre, String telefono, String email, Pago pago, int expiracionMinutos) {
        // El horario de atención se valida acá y no solo en la grilla: este método lo
        // alcanza el POST público de reservas, que antes aceptaba cualquier hora y
        // cualquier fecha con tal de que el rango estuviera libre.
        disponibilidadCanchaService.validarHorarioVendible(cancha.getId(), fecha, horaInicio, duracionMin);

        LocalDateTime inicioReal = disponibilidadCanchaService.inicioReal(cancha.getId(), fecha, horaInicio);
        if (inicioReal.isBefore(LocalDateTime.now())) {
            throw new EstadoInvalidoException("No se puede reservar un horario que ya pasó");
        }
        if (!disponibilidadCanchaService.rangoLibre(cancha.getId(), fecha, horaInicio, duracionMin)) {
            throw new EstadoInvalidoException("El horario de las " + horaInicio + " ya no está disponible");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Reserva reserva = Reserva.builder()
                .cancha(cancha)
                .fecha(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaInicio.plusMinutes(duracionMin))
                .duracionMin(duracionMin)
                .precioAplicado(disponibilidadCanchaService.precio(cancha, fecha, horaInicio, duracionMin))
                .estado(EstadoReserva.PENDIENTE)
                .clienteNombre(clienteNombre.trim())
                .clienteTelefono(telefono)
                .clienteEmail(email)
                .codigo(generarCodigo())
                .tokenPublico(UUID.randomUUID().toString())
                .creadoEn(ahora)
                .expiraEn(calcularExpiracion(cancha, ahora, inicioReal, pago, expiracionMinutos))
                .pago(pago)
                .cliente(clienteService.buscarOCrear(clienteNombre, telefono, email))
                .build();
        tomarHorario(reserva);

        try {
            // El flush explícito es lo que hace saltar el índice único acá y no al cerrar
            // la transacción, donde ya no se podría dar un mensaje entendible.
            reservaRepository.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            throw new EstadoInvalidoException("El horario de las " + horaInicio + " acaba de ser reservado por otra persona");
        }

        return reserva;
    }

    /**
     * Marca como tomados los bloques de 30 minutos que cubre el turno. El índice único
     * sobre la clave es lo que impide que dos personas compren el mismo horario en
     * simultáneo, aunque hayan pedido duraciones distintas.
     */
    private void tomarHorario(Reserva reserva) {
        reserva.getSlots().clear();
        for (String clave : disponibilidadCanchaService.clavesSlot(
                reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio(), reserva.getDuracionMin())) {
            reserva.getSlots().add(ReservaSlot.builder().reserva(reserva).claveSlot(clave).build());
        }
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
        // El jugador está esperando justo esta respuesta: pidió el turno y no sabe si lo
        // tiene. Si dejó mail, se entera solo en vez de tener que escribir preguntando.
        notificacionService.enviarComprobanteAlJugador(reserva, enlaceDelTurno(reserva));
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
     * El cliente reservó y no vino. No se libera el horario ni se borran sus bloques:
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
        boolean yaTermino = LocalDateTime.now().isAfter(inicioDe(reserva).plusMinutes(reserva.getDuracionMin()));
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

    /**
     * Momento real en que termina el turno. No se puede resolver comparando
     * {@code horaFin} contra la hora actual: un turno de 23 a 00 tiene horaFin 00:00, que
     * es menor que cualquier hora del día, y así se daba por jugado apenas amanecía. Lo
     * mismo pasaba al revés con la madrugada, que pertenece a la sesión del día anterior.
     *
     * <p>El mapa cachea la apertura por cancha: el barrido recorre todos los turnos vivos
     * y sin eso volvería a buscar el horario en cada uno.
     */
    private LocalDateTime finDe(Reserva reserva, Map<Long, LocalTime> aperturas) {
        if (reserva.getCancha() == null) {
            return reserva.getFecha().atTime(reserva.getHoraInicio())
                    .plusMinutes(reserva.getDuracionMin());
        }
        LocalTime apertura = aperturas.computeIfAbsent(reserva.getCancha().getId(),
                disponibilidadCanchaService::horaApertura);
        return disponibilidadCanchaService
                .inicioEnSesion(reserva.getFecha(), reserva.getHoraInicio(), apertura)
                .plusMinutes(reserva.getDuracionMin());
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
        LocalDateTime ahora = LocalDateTime.now();
        // La fecha de la reserva es la de la sesión, así que un turno de la madrugada de
        // hoy quedó guardado con la fecha de ayer: el margen de un día lo cubre.
        List<Reserva> candidatas = reservaRepository.findByEstadoAndFechaLessThanEqual(
                EstadoReserva.CONFIRMADA, ahora.toLocalDate());
        Map<Long, LocalTime> aperturas = new HashMap<>();
        int finalizados = 0;
        for (Reserva reserva : candidatas) {
            if (finDe(reserva, aperturas).isAfter(ahora)) {
                continue;
            }
            reserva.setEstado(EstadoReserva.FINALIZADA);
            reservaRepository.save(reserva);
            finalizados++;
        }
        return finalizados;
    }

    @Transactional(readOnly = true)
    public List<ReservaResponse> listarPorFecha(Long canchaId, LocalDate fecha) {
        return conMontos(reservaRepository.findByCanchaIdAndFecha(canchaId, fecha));
    }

    /**
     * Turnos vivos del día en todas las canchas: la vista del mostrador.
     *
     * <p>Ordenados por jornada y no por reloj: en un club que cierra a las 2, el turno de
     * la 1 AM es el último de la noche y tiene que quedar abajo de todo, no arriba del de
     * las 10 de la mañana.
     */
    @Transactional(readOnly = true)
    public List<ReservaResponse> listarDelDia(LocalDate fecha) {
        List<Reserva> reservas = new ArrayList<>(reservaRepository.findDelDiaConCancha(fecha, EN_JUEGO));
        // La apertura se resuelve una vez por cancha: pedirla por fila sería una consulta
        // de horario por cada turno del día en la pantalla más usada del club.
        Map<Long, LocalTime> aperturas = new HashMap<>();
        reservas.sort(Comparator
                .comparingInt((Reserva reserva) -> OrdenJornada.minutosDesdeApertura(
                        reserva.getHoraInicio(), aperturaDe(reserva, aperturas)))
                // Desempate por número de cancha: como texto, la 10 salía antes que la 2.
                .thenComparing(OrdenCanchas.porNombre(
                        reserva -> reserva.getCancha() != null ? reserva.getCancha().getNombre() : "")));

        List<ReservaResponse> respuestas = conMontos(reservas);
        for (int i = 0; i < respuestas.size(); i++) {
            Reserva reserva = reservas.get(i);
            respuestas.get(i).setInicioReal(disponibilidadCanchaService.inicioEnSesion(
                    reserva.getFecha(), reserva.getHoraInicio(), aperturaDe(reserva, aperturas)));
        }
        return respuestas;
    }

    /** La jornada que el club está atendiendo. Vive en el servicio de horarios. */
    @Transactional(readOnly = true)
    public LocalDate fechaDeJornadaActual() {
        return disponibilidadCanchaService.fechaDeJornadaActual();
    }

    private LocalTime aperturaDe(Reserva reserva, Map<Long, LocalTime> cache) {
        if (reserva.getCancha() == null) {
            return null;
        }
        return cache.computeIfAbsent(reserva.getCancha().getId(), disponibilidadCanchaService::horaApertura);
    }

    /**
     * Completa cada turno con lo cobrado y el consumo impago. Los dos totales salen de
     * una consulta agrupada cada uno: pedirlos por fila sería un N+1 sobre la pantalla
     * más usada del club.
     */
    private List<ReservaResponse> conMontos(List<Reserva> reservas) {
        if (reservas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = reservas.stream().map(Reserva::getId).toList();
        Map<Long, BigDecimal> cobrado = new HashMap<>();
        for (CobroRepository.TotalPorReserva total : cobroRepository.totalesPorReserva(ids)) {
            cobrado.put(total.getReservaId(), total.getTotal());
        }
        Map<Long, BigDecimal> consumo = new HashMap<>();
        for (VentaRepository.ConsumoPorReserva total : ventaRepository.consumoACuentaDe(ids)) {
            consumo.put(total.getReservaId(), total.getTotal());
        }
        return reservas.stream()
                .map(reserva -> reservaMapper.aResponse(
                        reserva, cobrado.get(reserva.getId()), consumo.get(reserva.getId())))
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

    /** Suelta el horario: los bloques se borran y la cancha vuelve a estar a la venta. */
    private void liberar(Reserva reserva, EstadoReserva nuevoEstado) {
        reserva.setEstado(nuevoEstado);
        reserva.getSlots().clear();
        reservaRepository.save(reserva);
    }

    private String generarCodigo() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * El enlace donde el jugador ve su turno. Sale de la URL del front, la misma que ya se
     * usa para volver de Mercado Pago: si se configurara aparte, el día que el club cambie
     * de dominio una de las dos quedaría vieja y nadie se enteraría hasta que un cliente
     * abriera un enlace muerto.
     */
    public String enlaceDelTurno(Reserva reserva) {
        return frontBaseUrl.replaceAll("/+$", "") + "/mi-turno/" + reserva.getTokenPublico();
    }

    /**
     * Recordatorios de los turnos que se vienen, para el que dejó su mail.
     *
     * <p>Se manda una sola vez por turno y solo si todavía queda tiempo de avisar que no
     * viene: un recordatorio que llega cuando ya no se puede cancelar no le sirve a nadie.
     */
    @Transactional
    public int enviarRecordatorios() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime desde = ahora.plusHours(HORAS_MINIMAS_RECORDATORIO);
        LocalDateTime hasta = ahora.plusHours(24);
        int enviados = 0;
        for (Reserva reserva : reservaRepository.findParaRecordar(
                ahora.toLocalDate().minusDays(1), hasta.toLocalDate())) {
            LocalDateTime inicio = disponibilidadCanchaService.inicioReal(
                    reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());
            if (inicio.isBefore(desde) || inicio.isAfter(hasta)) {
                continue;
            }
            notificacionService.recordarTurnoAlJugador(reserva, enlaceDelTurno(reserva));
            reserva.setRecordatorioEnviadoEn(ahora);
            reservaRepository.save(reserva);
            enviados++;
        }
        return enviados;
    }

    // ── El turno visto por el jugador ──────────────────────────────────────────────
    // Sin cuenta ni contraseña: manda el token del enlace, que autoriza ese turno y nada
    // más. Pedir un usuario antes de reservar es la forma más segura de que la persona
    // termine escribiendo por WhatsApp, que es de lo que el club se quiere sacar de encima.

    @Transactional(readOnly = true)
    public TurnoPublicoResponse verPorToken(String token) {
        return aTurnoPublico(porToken(token));
    }

    /**
     * El jugador cancela su propio turno. Libera el horario en el acto —que es lo que al
     * club le sirve, porque esa hora se puede volver a vender— y le avisa por mail, porque
     * si no se entera cuando ya es tarde.
     *
     * <p>La seña ya cobrada NO se devuelve sola: no hay devolución automática contra
     * Mercado Pago y prometerla en pantalla sería mentir. La pantalla lo dice antes de
     * confirmar y el club resuelve el caso si corresponde.
     */
    @Transactional
    public TurnoPublicoResponse cancelarPorToken(String token) {
        Reserva reserva = porToken(token);
        String motivo = motivoNoCancelable(reserva);
        if (motivo != null) {
            throw new EstadoInvalidoException(motivo);
        }
        liberar(reserva, EstadoReserva.CANCELADA);
        notificacionService.avisarCancelacionDelJugador(reserva);
        return aTurnoPublico(reserva);
    }

    private Reserva porToken(String token) {
        return reservaRepository.findByTokenPublico(token)
                .orElseThrow(() -> new ResourceNotFoundException("Turno", token));
    }

    /**
     * Por qué este turno no se puede cancelar por internet, o null si se puede.
     *
     * <p>La hora se compara sobre el arranque REAL del turno: uno de las 00:30 pertenece a
     * la jornada del día anterior, y restarle horas a un `LocalTime` suelto daría que
     * todavía falta un día entero.
     */
    private String motivoNoCancelable(Reserva reserva) {
        int horasMinimas = configuracionSedeService.horasMinimasCancelacion();
        if (horasMinimas <= 0) {
            return "Este club no toma cancelaciones por internet. Escribinos y lo resolvemos.";
        }
        if (reserva.getEstado() != EstadoReserva.PENDIENTE && reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            return "Este turno ya no está activo.";
        }
        LocalDateTime inicio = disponibilidadCanchaService.inicioReal(
                reserva.getCancha().getId(), reserva.getFecha(), reserva.getHoraInicio());
        if (LocalDateTime.now().isAfter(inicio.minusHours(horasMinimas))) {
            return "Los turnos se cancelan hasta " + horasMinimas
                    + (horasMinimas == 1 ? " hora antes." : " horas antes.")
                    + " Para este ya pasó el plazo: escribinos y lo vemos.";
        }
        return null;
    }

    private TurnoPublicoResponse aTurnoPublico(Reserva reserva) {
        Cancha cancha = reserva.getCancha();
        String motivo = motivoNoCancelable(reserva);
        BigDecimal cobrado = cobroRepository.totalCobradoDe(reserva.getId());
        return TurnoPublicoResponse.builder()
                .codigo(reserva.getCodigo())
                .clienteNombre(reserva.getClienteNombre())
                .canchaNombre(cancha != null ? cancha.getNombre() : null)
                .lugarNombre(cancha != null && cancha.getLugar() != null ? cancha.getLugar().getNombre() : null)
                .fecha(reserva.getFecha())
                .horaInicio(reserva.getHoraInicio())
                .horaFin(reserva.getHoraFin())
                .estado(reserva.getEstado() != null ? reserva.getEstado().name() : null)
                .precio(MontosReserva.precio(reserva))
                .seniaPagada(MontosReserva.seniaPagada(reserva))
                .saldoPendiente(MontosReserva.saldo(reserva, cobrado))
                .puedeCancelar(motivo == null)
                .motivoNoCancelable(motivo)
                .horasMinimasCancelacion(configuracionSedeService.horasMinimasCancelacion())
                .build();
    }

    private ReservaResponse aResponse(Reserva reserva) {
        return reservaMapper.aResponse(reserva);
    }
}
