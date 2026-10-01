package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
import com.padel.rankpadel.dto.response.DisponibilidadAbonosResponse;
import com.padel.rankpadel.dto.response.GeneracionTurnosFijosResponse;
import com.padel.rankpadel.dto.response.TurnoFijoResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.HorarioCancha;
import com.padel.rankpadel.entity.TurnoFijo;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.mapper.TurnoFijoMapper;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.TurnoFijoRepository;
import com.padel.rankpadel.util.OrdenCanchas;

import lombok.RequiredArgsConstructor;

/**
 * Turnos fijos (abonos). El club los define una vez y el sistema genera las reservas
 * concretas con anticipación, para que ocupen la grilla como cualquier otro turno.
 */
@Service
@RequiredArgsConstructor
public class TurnoFijoService {

    private static final Logger log = LoggerFactory.getLogger(TurnoFijoService.class);

    private static final String[] NOMBRES_DIA = {
            "lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos" };
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** En el mensaje de error alcanza con las primeras fechas: la lista completa no se lee. */
    private static final int MAX_CONFLICTOS_A_MOSTRAR = 4;

    /** Para la grilla de abonos libres, que nombra un día suelto y no una repetición. */
    private static final String[] NOMBRES_DIA_SINGULAR = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo" };

    /** Un hueco más corto que esto no alcanza para un turno, así que no se publica. */
    private static final int MINIMO_VENDIBLE_MIN = 60;

    private static final int MINUTOS_DEL_DIA = 24 * 60;

    private final TurnoFijoRepository turnoFijoRepository;
    private final CanchaRepository canchaRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;
    private final ClienteService clienteService;
    private final DisponibilidadCanchaService disponibilidadCanchaService;
    private final NotificacionService notificacionService;
    private final TurnoFijoMapper turnoFijoMapper;

    @Value("${app.turnos-fijos.semanas-adelante:6}")
    private int semanasAdelante;

    @Transactional(readOnly = true)
    public List<TurnoFijoResponse> listar(Long lugarId, Long canchaId) {
        return turnoFijoRepository.buscar(lugarId, canchaId).stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * La grilla de "turnos fijos disponibles": qué le queda al club por vender como abono,
     * día por día y cancha por cancha.
     *
     * <p>Lo que ocupa es el abono de otro cliente, no el turno suelto de la semana que
     * viene: un abono se toma todas las semanas, y que alguien haya reservado un martes no
     * impide venderle los martes a otro. Un abono con vigencia futura también ocupa — está
     * vendido, aunque todavía no haya empezado.
     *
     * <p>Los huecos se calculan en minutos desde la apertura, no comparando horas sueltas:
     * con apertura 10 y cierre 02 un abono de las 23 va DESPUÉS de uno de las 15, y
     * ordenando por reloj quedaba al revés.
     */
    @Transactional(readOnly = true)
    public DisponibilidadAbonosResponse disponibilidadSemanal(Long lugarId) {
        LocalDate hoy = LocalDate.now();
        List<Cancha> canchas = (lugarId != null
                ? canchaRepository.findByLugarIdAndActivoTrue(lugarId)
                : canchaRepository.findByActivoTrue()).stream()
                .sorted(OrdenCanchas.porNombre(Cancha::getNombre))
                .toList();

        List<TurnoFijo> vigentes = turnoFijoRepository.buscar(lugarId, null).stream()
                .filter(TurnoFijo::isActivo)
                .filter(t -> t.getVigenteHasta() == null || !t.getVigenteHasta().isBefore(hoy))
                .toList();

        List<DisponibilidadAbonosResponse.Dia> dias = new ArrayList<>();
        for (int diaSemana = 1; diaSemana <= 7; diaSemana++) {
            List<DisponibilidadAbonosResponse.CanchaDelDia> delDia = new ArrayList<>();
            for (Cancha cancha : canchas) {
                delDia.add(libresDeLaCancha(cancha, diaSemana, vigentes));
            }
            dias.add(new DisponibilidadAbonosResponse.Dia(
                    diaSemana, NOMBRES_DIA_SINGULAR[diaSemana - 1], delDia));
        }

        return new DisponibilidadAbonosResponse(dias, canchas.stream()
                .map(c -> new DisponibilidadAbonosResponse.Cancha(c.getId(), c.getNombre()))
                .toList());
    }

    private DisponibilidadAbonosResponse.CanchaDelDia libresDeLaCancha(
            Cancha cancha, int diaSemana, List<TurnoFijo> vigentes) {
        HorarioCancha horario = disponibilidadCanchaService.horarioDe(cancha.getId());
        if (horario == null || !disponibilidadCanchaService.atiendeElDia(horario, diaSemana)) {
            return new DisponibilidadAbonosResponse.CanchaDelDia(
                    cancha.getId(), cancha.getNombre(), true, List.of());
        }

        LocalTime apertura = horario.getHoraApertura();
        int jornadaMin = duracionJornada(apertura, horario.getHoraCierre());

        List<int[]> ocupados = new ArrayList<>();
        for (TurnoFijo abono : vigentes) {
            if (abono.getDiaSemana() != diaSemana || !abono.getCancha().getId().equals(cancha.getId())) {
                continue;
            }
            int desde = desdeLaApertura(apertura, abono.getHoraInicio());
            int hasta = Math.min(jornadaMin, desde + abono.getDuracionMin());
            if (desde < jornadaMin && hasta > desde) {
                ocupados.add(new int[] { desde, hasta });
            }
        }
        ocupados.sort(Comparator.comparingInt(rango -> rango[0]));

        List<DisponibilidadAbonosResponse.Franja> franjas = new ArrayList<>();
        int cursor = 0;
        for (int[] rango : ocupados) {
            agregarSiVendible(franjas, apertura, cursor, rango[0]);
            cursor = Math.max(cursor, rango[1]);
        }
        agregarSiVendible(franjas, apertura, cursor, jornadaMin);

        return new DisponibilidadAbonosResponse.CanchaDelDia(
                cancha.getId(), cancha.getNombre(), false, franjas);
    }

    /**
     * Un hueco más corto que un turno no se puede vender, así que no se publica: si entre
     * dos abonos quedan cuarenta minutos, anunciarlos como disponibles hace que el club
     * atienda un llamado para decir que no.
     */
    private void agregarSiVendible(List<DisponibilidadAbonosResponse.Franja> franjas,
            LocalTime apertura, int desde, int hasta) {
        if (hasta - desde >= MINIMO_VENDIBLE_MIN) {
            franjas.add(new DisponibilidadAbonosResponse.Franja(
                    apertura.plusMinutes(desde), apertura.plusMinutes(hasta)));
        }
    }

    /**
     * Minutos desde la apertura hasta esa hora, avanzando en el reloj: 0 para la apertura
     * misma y 780 para las 23 de un club que abre a las 10.
     */
    private int desdeLaApertura(LocalTime apertura, LocalTime hora) {
        int minutos = (hora.toSecondOfDay() - apertura.toSecondOfDay()) / 60;
        return minutos < 0 ? minutos + MINUTOS_DEL_DIA : minutos;
    }

    /**
     * Cuánto dura la jornada. Se separa de {@link #desdeLaApertura} por el caso borde:
     * abrir y cerrar a la misma hora es un club abierto las 24 horas, no uno con la
     * jornada en cero.
     */
    private int duracionJornada(LocalTime apertura, LocalTime cierre) {
        int minutos = desdeLaApertura(apertura, cierre);
        return minutos == 0 ? MINUTOS_DEL_DIA : minutos;
    }

    @Transactional
    public TurnoFijoResponse crear(TurnoFijoRequest request) {
        Cancha cancha = canchaValida(request.getCanchaId());
        TurnoFijo turnoFijo = TurnoFijo.builder()
                .cancha(cancha)
                .diaSemana(request.getDiaSemana())
                .horaInicio(request.getHoraInicio())
                .duracionMin(duracionValida(cancha, request.getDuracionMin()))
                .clienteNombre(request.getClienteNombre().trim())
                .clienteTelefono(request.getClienteTelefono().trim())
                // La ficha se arma sola, igual que con una reserva suelta: el abonado es
                // el cliente que más plata deja, no puede quedar sin historial.
                .cliente(clienteService.buscarOCrear(request.getClienteNombre(), request.getClienteTelefono()))
                .precioPactado(request.getPrecioPactado())
                .vigenteDesde(request.getVigenteDesde())
                .vigenteHasta(request.getVigenteHasta())
                .activo(true)
                .notas(request.getNotas())
                .creadoEn(LocalDateTime.now())
                .build();
        validarVigencia(turnoFijo);
        validarSinSuperposicion(turnoFijo, null);
        turnoFijoRepository.save(turnoFijo);
        return aResponse(turnoFijo);
    }

    @Transactional
    public TurnoFijoResponse actualizar(Long id, TurnoFijoRequest request) {
        TurnoFijo turnoFijo = buscar(id);
        boolean cambiaElHorario = turnoFijo.getDiaSemana() != request.getDiaSemana()
                || !turnoFijo.getHoraInicio().equals(request.getHoraInicio())
                || !turnoFijo.getCancha().getId().equals(request.getCanchaId());

        // Las reservas ya generadas quedaron en el horario viejo: se liberan antes de
        // validar el nuevo, o el abono chocaría contra sus propios turnos. Si la
        // validación falla, la transacción vuelve todo atrás.
        if (cambiaElHorario) {
            reservaService.cancelarFuturasDeTurnoFijo(turnoFijo.getId(), LocalDate.now());
        }

        Cancha cancha = canchaValida(request.getCanchaId());
        turnoFijo.setCancha(cancha);
        turnoFijo.setDiaSemana(request.getDiaSemana());
        turnoFijo.setHoraInicio(request.getHoraInicio());
        turnoFijo.setDuracionMin(duracionValida(cancha, request.getDuracionMin()));
        turnoFijo.setClienteNombre(request.getClienteNombre().trim());
        turnoFijo.setClienteTelefono(request.getClienteTelefono().trim());
        turnoFijo.setCliente(clienteService.buscarOCrear(
                request.getClienteNombre(), request.getClienteTelefono()));
        turnoFijo.setPrecioPactado(request.getPrecioPactado());
        turnoFijo.setVigenteDesde(request.getVigenteDesde());
        turnoFijo.setVigenteHasta(request.getVigenteHasta());
        turnoFijo.setNotas(request.getNotas());
        validarVigencia(turnoFijo);
        validarSinSuperposicion(turnoFijo, turnoFijo.getId());
        turnoFijoRepository.save(turnoFijo);
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

    /**
     * Reactiva un abono dado de baja. El cliente que deja el abono y vuelve al mes es el
     * caso normal, y antes había que cargarlo de nuevo desde cero.
     *
     * <p>Se vuelve a validar la superposición: mientras estuvo de baja, ese día y esa hora
     * se le pudieron haber vendido a otro, y reactivarlo a ciegas dejaría dos abonos
     * pisados en la misma cancha.
     *
     * <p>Las reservas concretas no se regeneran acá: la generación tiene su propio manejo
     * de conflictos y su aviso por mail, así que el que reactiva llama después a
     * {@code /generar} —que es idempotente— y ve el resultado.
     */
    @Transactional
    public TurnoFijoResponse reactivar(Long id) {
        TurnoFijo turnoFijo = buscar(id);
        if (turnoFijo.isActivo()) {
            return aResponse(turnoFijo);
        }
        turnoFijo.setActivo(true);
        validarVigencia(turnoFijo);
        validarSinSuperposicion(turnoFijo, turnoFijo.getId());
        turnoFijoRepository.save(turnoFijo);
        return aResponse(turnoFijo);
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
        List<LocalDate> fechas = fechasAGenerar(turnoFijo);
        if (fechas.isEmpty()) {
            return 0;
        }

        // Las fechas ya generadas se saltean en cualquier estado: si el club dio de baja
        // la semana que el cliente avisó que no venía, no hay que volver a crearla.
        Set<LocalDate> yaGeneradas = new HashSet<>(
                reservaRepository.findFechasGeneradas(turnoFijo.getId(), fechas.get(0)));
        int generadas = 0;
        for (LocalDate fecha : fechas) {
            if (!yaGeneradas.contains(fecha)) {
                generadas += generarFecha(turnoFijo, fecha, conflictos);
            }
        }
        return generadas;
    }

    /** Fechas del día de la semana del abono dentro de la ventana de anticipación. */
    private List<LocalDate> fechasAGenerar(TurnoFijo turnoFijo) {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = turnoFijo.getVigenteDesde().isAfter(hoy) ? turnoFijo.getVigenteDesde() : hoy;
        LocalDate hasta = hoy.plusWeeks(semanasAdelante);
        if (turnoFijo.getVigenteHasta() != null && turnoFijo.getVigenteHasta().isBefore(hasta)) {
            hasta = turnoFijo.getVigenteHasta();
        }

        List<LocalDate> fechas = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (fecha.getDayOfWeek().getValue() == turnoFijo.getDiaSemana()) {
                fechas.add(fecha);
            }
        }
        return fechas;
    }

    private int generarFecha(TurnoFijo turnoFijo, LocalDate fecha,
            List<GeneracionTurnosFijosResponse.Conflicto> conflictos) {
        LocalTime horaInicio = turnoFijo.getHoraInicio();
        if (reservaService.crearParaTurnoFijo(turnoFijo, fecha, horaInicio, turnoFijo.getPrecioPactado())
                .isPresent()) {
            return 1;
        }
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
        return 0;
    }

    private void validarVigencia(TurnoFijo turnoFijo) {
        if (turnoFijo.getVigenteHasta() != null
                && turnoFijo.getVigenteHasta().isBefore(turnoFijo.getVigenteDesde())) {
            throw new EstadoInvalidoException("La fecha de fin no puede ser anterior a la de inicio");
        }
    }

    /**
     * Un abono no puede pisar otro abono ni turnos ya agendados. Antes esto se detectaba
     * recién al generar las reservas —el abono se guardaba igual y el club se enteraba por
     * un mail— así que se podían cargar dos turnos superpuestos en la misma cancha.
     */
    private void validarSinSuperposicion(TurnoFijo turnoFijo, Long idExcluido) {
        int inicio = minutos(turnoFijo.getHoraInicio());
        int fin = inicio + turnoFijo.getDuracionMin();

        for (TurnoFijo otro : turnoFijoRepository.findActivosDelDia(
                turnoFijo.getCancha().getId(), turnoFijo.getDiaSemana(), idExcluido)) {
            if (!vigenciasSeCruzan(turnoFijo, otro)) {
                continue;
            }
            int otroInicio = minutos(otro.getHoraInicio());
            int otroFin = otroInicio + otro.getDuracionMin();
            if (inicio < otroFin && otroInicio < fin) {
                throw new EstadoInvalidoException(
                        "Se superpone con el turno fijo de %s, que ya tiene esta cancha los %s de %s a %s."
                                .formatted(otro.getClienteNombre(), NOMBRES_DIA[otro.getDiaSemana() - 1],
                                        hhmm(otroInicio), hhmm(otroFin)));
            }
        }

        List<String> ocupadas = fechasOcupadas(turnoFijo);
        if (!ocupadas.isEmpty()) {
            throw new EstadoInvalidoException(
                    "Estos días ya tienen la cancha tomada en ese horario: %s. Liberá esos turnos o elegí otro horario."
                            .formatted(String.join(", ", ocupadas)));
        }
    }

    /** Fechas próximas en las que el abono no entraría porque el horario ya está tomado. */
    private List<String> fechasOcupadas(TurnoFijo turnoFijo) {
        List<String> ocupadas = new ArrayList<>();
        LocalTime desde = turnoFijo.getHoraInicio();
        for (LocalDate fecha : fechasAGenerar(turnoFijo)) {
            if (!disponibilidadCanchaService.rangoLibre(
                    turnoFijo.getCancha().getId(), fecha, desde, turnoFijo.getDuracionMin())) {
                ocupadas.add(fecha.format(FECHA) + " a las " + desde.format(HORA));
            }
            if (ocupadas.size() == MAX_CONFLICTOS_A_MOSTRAR) {
                break;
            }
        }
        return ocupadas;
    }

    private boolean vigenciasSeCruzan(TurnoFijo uno, TurnoFijo otro) {
        LocalDate finUno = uno.getVigenteHasta();
        LocalDate finOtro = otro.getVigenteHasta();
        boolean unoTerminaAntes = finUno != null && finUno.isBefore(otro.getVigenteDesde());
        boolean otroTerminaAntes = finOtro != null && finOtro.isBefore(uno.getVigenteDesde());
        return !unoTerminaAntes && !otroTerminaAntes;
    }

    private static int minutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    private static String hhmm(int minutosDelDia) {
        return "%02d:%02d".formatted((minutosDelDia / 60) % 24, minutosDelDia % 60);
    }

    /** El abono se agenda con una de las duraciones que la sucursal vende. */
    private int duracionValida(Cancha cancha, Integer pedida) {
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
        return turnoFijoMapper.aResponse(turnoFijo);
    }
}
