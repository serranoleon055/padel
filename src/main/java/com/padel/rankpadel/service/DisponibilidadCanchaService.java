package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.DisponibilidadSedeResponse;
import com.padel.rankpadel.dto.response.DisponibilidadSedeResponse.CanchaLibre;
import com.padel.rankpadel.dto.response.DisponibilidadSedeResponse.FranjaSede;
import com.padel.rankpadel.dto.response.OpcionDuracion;
import com.padel.rankpadel.dto.response.SlotDisponibilidad;
import com.padel.rankpadel.entity.BloqueoCancha;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.HorarioCancha;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.BloqueoCanchaRepository;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.HorarioCanchaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.util.OrdenCanchas;
import com.padel.rankpadel.util.OrdenJornada;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisponibilidadCanchaService {

    /**
     * Granularidad interna de la agenda. Es el máximo común divisor de las duraciones
     * que se pueden vender (60, 90, 120) y define el bloque que se reserva en
     * {@code reserva_slots}. No la toques sin migrar esa tabla.
     */
    public static final int GRANULARIDAD_MIN = 30;

    private static final int MINUTOS_POR_HORA = 60;
    private static final int DURACION_PARTIDO_MIN = 90;
    private static final List<Integer> DURACIONES_POR_DEFECTO = List.of(60, 120);
    private static final int MAXIMO_SLOTS = 200;
    private static final int DIAS_BUSQUEDA_APERTURA = 8;
    /**
     * Estados que mantienen el horario tomado. Van los cuatro que conservan sus bloques
     * en {@code reserva_slots}: un turno jugado o al que el cliente no vino sigue
     * ocupando la cancha, y mostrarlo como libre hacía que la grilla ofreciera un
     * horario que después no se podía guardar.
     */
    private static final Set<EstadoReserva> ESTADOS_ACTIVOS = Set.of(
            EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA,
            EstadoReserva.FINALIZADA, EstadoReserva.NO_SHOW);

    private final PromocionCanchaService promocionCanchaService;
    private final CanchaRepository canchaRepository;
    private final HorarioCanchaRepository horarioCanchaRepository;
    private final ReservaRepository reservaRepository;
    private final BloqueoCanchaRepository bloqueoCanchaRepository;
    private final PartidoRepository partidoRepository;

    /**
     * Horarios de inicio de una cancha en un día, cada uno con las duraciones que
     * realmente entran a partir de esa hora y lo que sale cada una.
     *
     * <p>Un horario aparece como disponible solo si al menos la duración más corta entra
     * completa antes del cierre y sin pisar nada.
     */
    public List<SlotDisponibilidad> slots(Long canchaId, LocalDate fecha) {
        HorarioCancha horario = horarioActivo(canchaId);
        if (horario == null || !fechaHabilitada(horario, fecha)) {
            return List.of();
        }
        // Trae la cancha para poder cotizar. Antes pasaba null fijo y cada opción de
        // duración salía con precio nulo: el endpoint público listaba las duraciones sin
        // decir cuánto sale ninguna, y la variante que sí cotizaba no la llamaba nadie.
        return slots(canchaId, fecha, horario, canchaRepository.findById(canchaId).orElse(null));
    }

    /** Igual que {@link #slots}, pero con la cancha ya cargada para no volver a buscarla. */
    public List<SlotDisponibilidad> slots(Cancha cancha, LocalDate fecha) {
        HorarioCancha horario = horarioActivo(cancha.getId());
        if (horario == null || !fechaHabilitada(horario, fecha)) {
            return List.of();
        }
        return slots(cancha.getId(), fecha, horario, cancha);
    }

    private List<SlotDisponibilidad> slots(Long canchaId, LocalDate fecha, HorarioCancha horario, Cancha cancha) {
        List<Integer> duraciones = duraciones(horario);
        int paso = paso(duraciones);

        LocalTime apertura = horario.getHoraApertura();
        LocalDateTime inicioSesion = fecha.atTime(apertura);
        LocalDateTime finSesion = fecha.atTime(horario.getHoraCierre());
        if (!finSesion.isAfter(inicioSesion)) {
            finSesion = finSesion.plusDays(1);
        }

        List<Intervalo> ocupaciones = ocupaciones(canchaId, fecha, apertura, inicioSesion, finSesion, null);
        List<SlotDisponibilidad> slots = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        LocalDateTime inicio = inicioSesion;
        int contador = 0;
        while (inicio.isBefore(finSesion) && contador++ < MAXIMO_SLOTS) {
            if (inicio.isAfter(ahora)) {
                List<OpcionDuracion> opciones = new ArrayList<>();
                for (int duracion : duraciones) {
                    LocalDateTime fin = inicio.plusMinutes(duracion);
                    // El turno tiene que terminar antes del cierre: ofrecer uno que se
                    // corta a la mitad es peor que no ofrecerlo.
                    if (fin.isAfter(finSesion) || !libre(ocupaciones, inicio, fin)) {
                        continue;
                    }
                    opciones.add(OpcionDuracion.builder()
                            .minutos(duracion)
                            .horaFin(fin.toLocalTime())
                            .precio(cancha != null ? precio(cancha, fecha, inicio.toLocalTime(), duracion) : null)
                            .build());
                }
                slots.add(SlotDisponibilidad.builder()
                        .horaInicio(inicio.toLocalTime())
                        .horaFin(inicio.plusMinutes(duraciones.get(0)).toLocalTime())
                        .disponible(!opciones.isEmpty())
                        .opciones(opciones)
                        .build());
            }
            inicio = inicio.plusMinutes(paso);
        }
        return slots;
    }

    /**
     * Cuántos turnos de cada duración vendible entran todavía hoy en esta cancha.
     *
     * <p>Los números NO se suman entre sí: un hueco de dos horas es un turno de 120 o dos
     * de 60, y el mostrador necesita las dos lecturas para saber qué ofrecer. Cada
     * duración se cuenta tomando siempre el hueco más temprano que entra, que es la forma
     * de meter la mayor cantidad posible sin superponerlas.
     */
    public Map<Integer, Long> turnosVendibles(Long canchaId, LocalDate fecha) {
        HorarioCancha horario = horarioActivo(canchaId);
        if (horario == null || !fechaHabilitada(horario, fecha)) {
            return Map.of();
        }
        List<Integer> duraciones = duraciones(horario);
        int paso = paso(duraciones);

        LocalTime apertura = horario.getHoraApertura();
        LocalDateTime inicioSesion = fecha.atTime(apertura);
        LocalDateTime finSesion = fecha.atTime(horario.getHoraCierre());
        if (!finSesion.isAfter(inicioSesion)) {
            finSesion = finSesion.plusDays(1);
        }

        List<Intervalo> ocupaciones = ocupaciones(canchaId, fecha, apertura, inicioSesion, finSesion, null);
        LocalDateTime ahora = LocalDateTime.now();

        Map<Integer, Long> vendibles = new TreeMap<>();
        for (int duracion : duraciones) {
            long cuentan = 0;
            LocalDateTime inicio = inicioSesion;
            int contador = 0;
            while (!inicio.plusMinutes(duracion).isAfter(finSesion) && contador++ < MAXIMO_SLOTS) {
                if (inicio.isAfter(ahora) && libre(ocupaciones, inicio, inicio.plusMinutes(duracion))) {
                    cuentan++;
                    // El turno siguiente arranca en el próximo horario vendible, no apenas
                    // termina éste: con turnos de 90 minutos, uno que empieza a las 20
                    // libera la cancha 21:30 pero el que sigue recién se puede vender a las 22.
                    inicio = proximoInicioVendible(inicioSesion, inicio.plusMinutes(duracion), paso);
                } else {
                    inicio = inicio.plusMinutes(paso);
                }
            }
            vendibles.put(duracion, cuentan);
        }
        return vendibles;
    }

    /**
     * Redondea {@code momento} hacia adelante hasta caer en un horario de inicio válido,
     * es decir un múltiplo de {@code paso} contado desde la apertura.
     */
    private LocalDateTime proximoInicioVendible(LocalDateTime inicioSesion, LocalDateTime momento, int paso) {
        long minutos = java.time.Duration.between(inicioSesion, momento).toMinutes();
        long resto = minutos % paso;
        return resto == 0 ? momento : momento.plusMinutes(paso - resto);
    }

    /**
     * La agenda de una sucursal vista por horario: cuántas canchas quedan a cada hora y,
     * dentro de cada una, qué duraciones entran y a qué precio. Reemplaza al select de
     * cancha, que obligaba al jugador a probar una por una para encontrar un hueco.
     */
    public DisponibilidadSedeResponse disponibilidadSede(Long lugarId, LocalDate fecha) {
        // Por número de cancha: dentro de cada horario, las canchas libres se listan como
        // el club las nombra. En orden de creación la 3 podía salir antes que la 1.
        List<Cancha> canchas = canchaRepository.findByLugarIdAndActivoTrue(lugarId)
                .stream().sorted(OrdenCanchas.porNombre(Cancha::getNombre)).toList();

        // Ordenado por jornada, no por reloj: si el club abre a las 10 y cierra a las 2,
        // las 00 y la 1 son los últimos turnos de la noche y van al final de la grilla.
        Map<LocalTime, List<CanchaLibre>> porHorario =
                new TreeMap<>(OrdenJornada.comparador(aperturaDeReferencia(canchas)));
        for (Cancha cancha : canchas) {
            for (SlotDisponibilidad slot : slots(cancha, fecha)) {
                if (!slot.isDisponible()) {
                    continue;
                }
                porHorario.computeIfAbsent(slot.getHoraInicio(), hora -> new ArrayList<>())
                        .add(CanchaLibre.builder()
                                .canchaId(cancha.getId())
                                .canchaNombre(cancha.getNombre())
                                .tipo(cancha.getDescripcion())
                                .opciones(slot.getOpciones())
                                .build());
            }
        }

        List<FranjaSede> franjas = new ArrayList<>();
        porHorario.forEach((hora, libres) -> franjas.add(FranjaSede.builder()
                .horaInicio(hora)
                .canchasDisponibles(libres.size())
                .precioDesde(precioMasBajo(libres))
                .enPromocion(canchas.stream().anyMatch(cancha -> hayPromocion(cancha.getId(), fecha, hora)))
                .canchas(libres)
                .build()));
        return DisponibilidadSedeResponse.builder().franjas(franjas).build();
    }

    /**
     * Hora en que arranca la jornada de una sucursal: la apertura más temprana de sus
     * canchas. Si dos canchas abren a horas distintas, la grilla de la sede es una sola y
     * tiene que empezar en la primera que abre.
     */
    private LocalTime aperturaDeReferencia(List<Cancha> canchas) {
        return canchas.stream()
                .map(cancha -> aperturaConfigurada(cancha.getId()))
                .filter(java.util.Objects::nonNull)
                .min(LocalTime::compareTo)
                .orElse(LocalTime.MIN);
    }

    private BigDecimal precioMasBajo(List<CanchaLibre> libres) {
        return libres.stream()
                .flatMap(libre -> libre.getOpciones().stream())
                .map(OpcionDuracion::getPrecio)
                .filter(java.util.Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private boolean hayPromocion(Long canchaId, LocalDate fecha, LocalTime hora) {
        return promocionCanchaService.precioPorHora(canchaId, fecha, hora) != null;
    }

    /** Duraciones que vende el club para esta cancha, ordenadas de menor a mayor. */
    public List<Integer> duracionesOfrecidas(Long canchaId) {
        return duraciones(horarioActivo(canchaId));
    }

    private List<Integer> duraciones(HorarioCancha horario) {
        if (horario == null || horario.getDuracionesOfrecidas() == null || horario.getDuracionesOfrecidas().isBlank()) {
            return DURACIONES_POR_DEFECTO;
        }
        List<Integer> duraciones = new ArrayList<>();
        for (String token : horario.getDuracionesOfrecidas().split(",")) {
            try {
                int minutos = Integer.parseInt(token.trim());
                if (minutos > 0 && minutos % GRANULARIDAD_MIN == 0 && !duraciones.contains(minutos)) {
                    duraciones.add(minutos);
                }
            } catch (NumberFormatException ignorada) {
                // Un valor roto en la configuración no puede dejar la agenda sin horarios.
            }
        }
        duraciones.sort(Comparator.naturalOrder());
        return duraciones.isEmpty() ? DURACIONES_POR_DEFECTO : duraciones;
    }

    /**
     * Cada cuánto arranca un turno: la hora en punto.
     *
     * <p>Antes salía del máximo común divisor de las duraciones, y agregar los 90 minutos
     * llenaba la grilla de inicios "y media". En la cancha nadie reserva a las 20:30: se
     * juega a las 20 o a las 21, y un turno de hora y media termina 21:30 dejando esa
     * media hora muerta, que es una decisión del club y no algo que la grilla tenga que
     * repartir sola. Solo se baja de la hora si el club llega a vender turnos más cortos.
     */
    private int paso(List<Integer> duraciones) {
        int masCorta = duraciones.get(0);
        return masCorta < MINUTOS_POR_HORA ? Math.max(GRANULARIDAD_MIN, masCorta) : MINUTOS_POR_HORA;
    }

    public boolean rangoLibre(Long canchaId, LocalDate fecha, LocalTime inicio, int duracionMin) {
        LocalTime apertura = aperturaActiva(canchaId);
        LocalDateTime inicioReal = aDateTime(fecha, inicio, apertura);
        LocalDateTime ventanaInicio = fecha.atTime(apertura);
        LocalDateTime ventanaFin = ventanaInicio.plusDays(1);
        return libre(ocupaciones(canchaId, fecha, apertura, ventanaInicio, ventanaFin, null),
                inicioReal, inicioReal.plusMinutes(duracionMin));
    }

    public boolean canchaLibreParaPartido(Long canchaId, LocalDateTime inicio, Long partidoIdExcluido) {
        LocalDate fecha = inicio.toLocalDate();
        LocalDateTime ventanaInicio = fecha.atStartOfDay();
        LocalDateTime ventanaFin = fecha.plusDays(1).atStartOfDay();
        return libre(ocupaciones(canchaId, fecha, LocalTime.MIN, ventanaInicio, ventanaFin, partidoIdExcluido),
                inicio, inicio.plusMinutes(DURACION_PARTIDO_MIN));
    }

    public LocalDateTime inicioReal(Long canchaId, LocalDate fecha, LocalTime hora) {
        return aDateTime(fecha, hora, aperturaActiva(canchaId));
    }

    /**
     * Hora a la que abre el club para esta cancha. La expone para quien recorre muchos
     * turnos de la misma cancha y no quiere volver a buscar el horario en cada uno.
     */
    public LocalTime horaApertura(Long canchaId) {
        return aperturaActiva(canchaId);
    }

    /**
     * Momento real de arranque de un horario dentro de la sesión de {@code fecha}, con la
     * apertura ya resuelta. Un turno anterior a la apertura pertenece a la madrugada del
     * día siguiente: es la misma jornada del club, no la fecha de calendario.
     */
    public LocalDateTime inicioEnSesion(LocalDate fecha, LocalTime hora, LocalTime apertura) {
        return aDateTime(fecha, hora, apertura);
    }

    /** La duración más corta que vende el club: la que se usa si no eligieron ninguna. */
    public int duracionPorDefecto(Long canchaId) {
        return duracionesOfrecidas(canchaId).get(0);
    }

    /**
     * Precio de un turno completo. Se cotiza bloque de 30 minutos por bloque de 30
     * minutos: un turno que arranca dentro de una promoción y termina fuera paga cada
     * tramo a su precio, que es como el club lo cobraría a mano.
     */
    public BigDecimal precio(Cancha cancha, LocalDate fecha, LocalTime horaInicio, int duracionMin) {
        if (cancha == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int minuto = 0; minuto < duracionMin; minuto += GRANULARIDAD_MIN) {
            LocalTime hora = horaInicio.plusMinutes(minuto);
            BigDecimal porHora = promocionCanchaService.precioPorHora(cancha.getId(), fecha, hora);
            if (porHora == null) {
                porHora = cancha.getPrecioPorHora();
            }
            if (porHora == null) {
                return null;
            }
            total = total.add(porHora
                    .multiply(BigDecimal.valueOf(GRANULARIDAD_MIN))
                    .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Bloques de 30 minutos que ocupa un turno, en el formato de {@code reserva_slots}. */
    public List<String> clavesSlot(Long canchaId, LocalDate fecha, LocalTime horaInicio, int duracionMin) {
        List<String> claves = new ArrayList<>();
        for (int minuto = 0; minuto < duracionMin; minuto += GRANULARIDAD_MIN) {
            claves.add(canchaId + "|" + fecha + "|" + horaInicio.plusMinutes(minuto));
        }
        return claves;
    }

    /**
     * Primer momento, a partir de {@code desde}, en que el club está abierto para esta cancha.
     * Si {@code desde} ya cae dentro del horario de atención, devuelve {@code desde}.
     * Se usa para no expirar solicitudes mientras el club está cerrado y no puede verlas.
     */
    public LocalDateTime proximaApertura(Long canchaId, LocalDateTime desde) {
        HorarioCancha horario = horarioActivo(canchaId);
        if (horario == null || horario.getHoraApertura() == null || horario.getHoraCierre() == null) {
            return desde;
        }
        for (int i = 0; i <= DIAS_BUSQUEDA_APERTURA; i++) {
            LocalDate dia = desde.toLocalDate().plusDays(i);
            if (!diaActivo(horario.getDiasActivos(), dia)) {
                continue;
            }
            LocalDateTime apertura = dia.atTime(horario.getHoraApertura());
            LocalDateTime cierre = dia.atTime(horario.getHoraCierre());
            if (!cierre.isAfter(apertura)) {
                cierre = cierre.plusDays(1);
            }
            if (desde.isBefore(apertura)) {
                return apertura;
            }
            if (desde.isBefore(cierre)) {
                return desde;
            }
        }
        return desde;
    }

    /**
     * Fecha de la jornada que el club está atendiendo ahora. Antes de la apertura la
     * sesión viva es la que arrancó ayer: a la 1 AM hay gente en la cancha anotada con la
     * fecha de ayer, y preguntar por "hoy" devolvía el día que todavía no empezó.
     */
    public LocalDate fechaDeJornadaActual() {
        LocalDateTime ahora = LocalDateTime.now();
        // Solo cuentan las canchas con horario cargado: una sin configurar devolvía las
        // 00:00 y tiraba la apertura de referencia a medianoche, con lo que la jornada
        // pasaba a ser siempre la de hoy y el arreglo no servía para nada.
        LocalTime apertura = canchaRepository.findByActivoTrue().stream()
                .map(cancha -> aperturaConfigurada(cancha.getId()))
                .filter(java.util.Objects::nonNull)
                .min(LocalTime::compareTo)
                .orElse(LocalTime.MIN);
        return ahora.toLocalTime().isBefore(apertura) ? ahora.toLocalDate().minusDays(1) : ahora.toLocalDate();
    }

    private LocalTime aperturaActiva(Long canchaId) {
        LocalTime apertura = aperturaConfigurada(canchaId);
        return apertura != null ? apertura : LocalTime.MIN;
    }

    /** Apertura de la cancha, o null si no tiene horario cargado. */
    private LocalTime aperturaConfigurada(Long canchaId) {
        HorarioCancha horario = horarioActivo(canchaId);
        return horario != null ? horario.getHoraApertura() : null;
    }

    private LocalDateTime aDateTime(LocalDate fecha, LocalTime hora, LocalTime apertura) {
        return hora.isBefore(apertura) ? fecha.plusDays(1).atTime(hora) : fecha.atTime(hora);
    }

    /**
     * Valida que el club realmente venda ese horario: día habilitado, dentro de la
     * anticipación permitida, y el turno entero entre la apertura y el cierre.
     *
     * <p>La grilla ya filtra todo esto al mostrar, pero el POST de reservas es público y
     * no volvía a chequearlo: entraban turnos a las 5 de la mañana con el club cerrado, o
     * a tres meses vista cuando la cancha se vende con dos semanas de anticipación. La
     * duración sí se validaba; a estas dos se les había pasado.
     */
    public void validarHorarioVendible(Long canchaId, LocalDate fecha, LocalTime horaInicio, int duracionMin) {
        HorarioCancha horario = horarioActivo(canchaId);
        if (horario == null) {
            throw new EstadoInvalidoException("Esta cancha todavía no tiene horario de atención cargado.");
        }

        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy)) {
            throw new EstadoInvalidoException("No se puede reservar un día que ya pasó");
        }
        if (horario.getAnticipacionDias() > 0 && fecha.isAfter(hoy.plusDays(horario.getAnticipacionDias()))) {
            throw new EstadoInvalidoException("Los turnos se pueden sacar con hasta "
                    + horario.getAnticipacionDias() + " días de anticipación.");
        }
        if (!diaActivo(horario.getDiasActivos(), fecha)) {
            throw new EstadoInvalidoException("El club no atiende ese día.");
        }

        // La sesión puede cruzar medianoche (abre 10, cierra 02): el cierre se compara
        // sobre la línea de tiempo real, no contra el reloj.
        LocalTime apertura = horario.getHoraApertura();
        LocalDateTime inicioSesion = fecha.atTime(apertura);
        LocalDateTime finSesion = fecha.atTime(horario.getHoraCierre());
        if (!finSesion.isAfter(inicioSesion)) {
            finSesion = finSesion.plusDays(1);
        }
        LocalDateTime inicio = aDateTime(fecha, horaInicio, apertura);
        if (inicio.isBefore(inicioSesion) || inicio.plusMinutes(duracionMin).isAfter(finSesion)) {
            throw new EstadoInvalidoException("El club atiende de " + apertura + " a "
                    + horario.getHoraCierre() + ". El turno tiene que entrar completo en ese horario.");
        }
    }

    private HorarioCancha horarioActivo(Long canchaId) {
        List<HorarioCancha> horarios = horarioCanchaRepository.findByCanchaIdAndActivoTrue(canchaId);
        return horarios.isEmpty() ? null : horarios.get(0);
    }

    private boolean fechaHabilitada(HorarioCancha horario, LocalDate fecha) {
        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy)) {
            return false;
        }
        if (horario.getAnticipacionDias() > 0 && fecha.isAfter(hoy.plusDays(horario.getAnticipacionDias()))) {
            return false;
        }
        return diaActivo(horario.getDiasActivos(), fecha);
    }

    private boolean diaActivo(String diasActivos, LocalDate fecha) {
        if (diasActivos == null || diasActivos.isBlank()) {
            return true;
        }
        String dia = String.valueOf(fecha.getDayOfWeek().getValue());
        for (String token : diasActivos.split(",")) {
            if (token.trim().equals(dia)) {
                return true;
            }
        }
        return false;
    }

    private List<Intervalo> ocupaciones(Long canchaId, LocalDate fecha, LocalTime apertura,
            LocalDateTime ventanaInicio, LocalDateTime ventanaFin, Long partidoIdExcluido) {
        List<Intervalo> intervalos = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        for (Reserva reserva : reservaRepository.findByCanchaIdAndFecha(canchaId, fecha)) {
            if (!ESTADOS_ACTIVOS.contains(reserva.getEstado())) {
                continue;
            }
            if (reserva.getEstado() == EstadoReserva.PENDIENTE
                    && reserva.getExpiraEn() != null
                    && reserva.getExpiraEn().isBefore(ahora)) {
                continue;
            }
            LocalDateTime inicio = aDateTime(fecha, reserva.getHoraInicio(), apertura);
            intervalos.add(new Intervalo(inicio, inicio.plusMinutes(reserva.getDuracionMin())));
        }

        for (BloqueoCancha bloqueo : bloqueoCanchaRepository.findByCanchaId(canchaId)) {
            intervalos.add(new Intervalo(bloqueo.getInicio(), bloqueo.getFin()));
        }

        for (Partido partido : partidoRepository.findByCanchaIdAndFechaHoraProgramadaBetween(
                canchaId, ventanaInicio, ventanaFin)) {
            if (partido.getFechaHoraProgramada() == null) {
                continue;
            }
            if (partidoIdExcluido != null && partidoIdExcluido.equals(partido.getId())) {
                continue;
            }
            intervalos.add(new Intervalo(partido.getFechaHoraProgramada(),
                    partido.getFechaHoraProgramada().plusMinutes(DURACION_PARTIDO_MIN)));
        }

        return intervalos;
    }

    private boolean libre(List<Intervalo> ocupaciones, LocalDateTime inicio, LocalDateTime fin) {
        for (Intervalo ocupado : ocupaciones) {
            if (inicio.isBefore(ocupado.fin()) && ocupado.inicio().isBefore(fin)) {
                return false;
            }
        }
        return true;
    }

    private record Intervalo(LocalDateTime inicio, LocalDateTime fin) {
    }
}
