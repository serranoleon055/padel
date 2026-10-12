package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.config.Instalacion;
import com.padel.rankpadel.dto.request.AplicarFixtureRequest;
import com.padel.rankpadel.dto.request.MoverPartidoRequest;
import com.padel.rankpadel.dto.request.PropuestaFixtureRequest;
import com.padel.rankpadel.dto.request.ReglasFixtureRequest;
import com.padel.rankpadel.dto.response.FixtureResponse;
import com.padel.rankpadel.dto.response.PropuestaFixtureResponse;
import com.padel.rankpadel.dto.response.ReglasFixtureResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;
import com.padel.rankpadel.entity.FranjaSede;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.RestriccionHoraria;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.entity.TorneoSede;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.enums.FormatoPartido;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.LugarRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.repository.TorneoSedeRepository;
import com.padel.rankpadel.service.ConflictosFixture.Conflicto;
import com.padel.rankpadel.service.ConflictosFixture.PartidoAgendado;
import com.padel.rankpadel.service.ProgramadorFixture.Franja;
import com.padel.rankpadel.service.ProgramadorFixture.Ocupacion;
import com.padel.rankpadel.service.ProgramadorFixture.PartidoFijo;
import com.padel.rankpadel.service.ProgramadorFixture.PartidoPendiente;
import com.padel.rankpadel.util.FormatoPorFase;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Los horarios del torneo: las reglas para armarlos, la propuesta automática, el ajuste a
 * mano y lo que quedó roto. El cálculo vive en {@link ProgramadorFixture} y
 * {@link ConflictosFixture}; acá se junta lo que necesitan desde la base.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureService {

    static final int DESCANSO_POR_DEFECTO = 30;

    private static final Set<EstadoPartido> SIN_JUGAR = Set.of(EstadoPartido.PENDIENTE, EstadoPartido.EN_CURSO);

    private final TorneoRepository torneoRepository;
    private final TorneoSedeRepository torneoSedeRepository;
    private final PartidoRepository partidoRepository;
    private final CanchaRepository canchaRepository;
    private final LugarRepository lugarRepository;
    private final DisponibilidadCanchaService disponibilidadCanchaService;
    private final Instalacion instalacion;

    // ── Reglas ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReglasFixtureResponse reglas(Long torneoId) {
        Torneo torneo = buscarTorneo(torneoId);
        return ReglasFixtureResponse.builder()
                .descansoMinimoMinutos(descanso(torneo))
                .categorias(torneo.getConfiguracionesCategoria().stream()
                        .filter(c -> c.getCategoria() != null)
                        .sorted(Comparator.comparing(c -> c.getCategoria().getNombre()))
                        .map(c -> ReglasFixtureResponse.ReglaCategoria.builder()
                                .categoriaId(c.getCategoria().getId())
                                .categoriaNombre(c.getCategoria().getNombre())
                                .duracionPartidoMinutos(c.getDuracionPartidoMinutos())
                                .duracionSugerida(duracionSugerida(c))
                                .lugarFijoId(c.getLugarFijo() != null ? c.getLugarFijo().getId() : null)
                                .lugarFijoNombre(c.getLugarFijo() != null ? c.getLugarFijo().getNombre() : null)
                                .build())
                        .toList())
                .build();
    }

    @Transactional
    public ReglasFixtureResponse guardarReglas(Long torneoId, ReglasFixtureRequest request) {
        Torneo torneo = buscarTorneo(torneoId);
        torneo.setDescansoMinimoMinutos(request.getDescansoMinimoMinutos());
        for (ReglasFixtureRequest.ReglaCategoria regla : request.getCategorias()) {
            ConfiguracionCategoriaTorneo config = torneo.getConfiguracionesCategoria().stream()
                    .filter(c -> c.getCategoria() != null && c.getCategoria().getId().equals(regla.getCategoriaId()))
                    .findFirst()
                    .orElseThrow(() -> new EstadoInvalidoException("Esa categoría no es de este torneo."));
            config.setDuracionPartidoMinutos(regla.getDuracionPartidoMinutos());
            if (regla.getLugarFijoId() == null) {
                config.setLugarFijo(null);
            } else {
                if (!torneoSedeRepository.existsByTorneoIdAndLugarId(torneoId, regla.getLugarFijoId())) {
                    throw new EstadoInvalidoException("El club fijado para " + config.getCategoria().getNombre()
                            + " tiene que ser uno de los clubes del torneo.");
                }
                Lugar lugar = lugarRepository.findById(regla.getLugarFijoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Club", regla.getLugarFijoId()));
                config.setLugarFijo(lugar);
            }
        }
        torneoRepository.save(torneo);
        return reglas(torneoId);
    }

    // ── Agenda y conflictos ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FixtureResponse agenda(Long torneoId) {
        Contexto ctx = contexto(buscarTorneo(torneoId));
        List<Conflicto> conflictos = conflictos(ctx);

        List<FixtureResponse.PartidoFixture> partidos = ctx.partidos.stream()
                .filter(p -> p.getEstado() != EstadoPartido.BYE)
                .sorted(Comparator.comparing((Partido p) -> p.getFechaHoraProgramada() == null)
                        .thenComparing(p -> p.getFechaHoraProgramada(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Partido::getId))
                .map(p -> aPartidoFixture(p, ctx))
                .toList();

        return FixtureResponse.builder()
                .descansoMinimoMinutos(ctx.descanso)
                .partidos(partidos)
                .conflictos(conflictos.stream()
                        .map(c -> FixtureResponse.Conflicto.builder()
                                .tipo(c.tipo().name())
                                .grave(c.grave())
                                .partidoIds(c.partidoIds())
                                .mensaje(c.mensaje())
                                .build())
                        .toList())
                .sinProgramar((int) partidos.stream().filter(p -> p.isProgramable() && p.getInicio() == null).count())
                .build();
    }

    // ── Propuesta automática ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PropuestaFixtureResponse proponer(Long torneoId, PropuestaFixtureRequest request) {
        Torneo torneo = buscarTorneo(torneoId);
        Contexto ctx = contexto(torneo);
        if (ctx.partidos.stream().noneMatch(this::programable)) {
            throw new EstadoInvalidoException("No hay partidos para programar: primero hay que hacer el sorteo "
                    + "(o esperar a que se definan los cruces de la siguiente ronda).");
        }
        if (ctx.franjas.isEmpty()) {
            throw new EstadoInvalidoException("Cargá en la pestaña Clubes los días y horarios en que cada club "
                    + "cede sus canchas: el fixture se arma dentro de esas franjas.");
        }

        LocalDateTime desde = request.getDesde() != null ? request.getDesde() : LocalDateTime.now();
        if (request.getDesde() == null && torneo.getFechaInicio() != null && torneo.getFechaInicio().atStartOfDay().isAfter(desde)) {
            desde = torneo.getFechaInicio().atStartOfDay();
        }

        List<PartidoPendiente> pendientes = new ArrayList<>();
        List<PartidoFijo> fijos = new ArrayList<>();
        int respetados = 0;
        for (Partido partido : ctx.partidos) {
            boolean deLaCategoria = request.getCategoriaId() == null
                    || request.getCategoriaId().equals(idCategoria(partido));
            boolean sinHorario = partido.getFechaHoraProgramada() == null;
            boolean seMueve = programable(partido) && deLaCategoria
                    && (sinHorario || (request.isReprogramar() && !partido.getFechaHoraProgramada().isBefore(desde)));
            if (seMueve) {
                pendientes.add(aPendiente(partido, ctx));
            } else if (!sinHorario && SIN_JUGAR.contains(partido.getEstado())) {
                fijos.add(aFijo(partido, ctx));
                respetados++;
            }
        }
        if (pendientes.isEmpty()) {
            throw new EstadoInvalidoException("Todos los partidos que se pueden jugar ya tienen horario. "
                    + "Si querés rehacerlos, marcá \"Volver a armar los que ya tienen horario\".");
        }
        ctx.ajenos.forEach(p -> fijos.add(aFijo(p, ctx)));

        ProgramadorFixture.Resultado resultado = ProgramadorFixture.programar(
                ctx.franjas, ctx.reservas, fijos, pendientes, ctx.descanso, desde);

        Map<Long, Partido> porId = ctx.partidos.stream().collect(Collectors.toMap(Partido::getId, p -> p));
        Map<Long, Cancha> canchas = ctx.canchas;
        return PropuestaFixtureResponse.builder()
                .asignaciones(resultado.asignaciones().stream()
                        .map(a -> {
                            Partido partido = porId.get(a.partidoId());
                            Cancha cancha = canchas.get(a.canchaId());
                            return PropuestaFixtureResponse.Asignacion.builder()
                                    .partidoId(a.partidoId())
                                    .descripcion(descripcion(partido))
                                    .categoriaNombre(nombreCategoria(partido))
                                    .canchaId(cancha.getId())
                                    .canchaNombre(cancha.getNombre())
                                    .lugarId(cancha.getLugar().getId())
                                    .lugarNombre(cancha.getLugar().getNombre())
                                    .inicio(a.inicio())
                                    .fin(a.fin())
                                    .build();
                        })
                        .toList())
                .sinLugar(resultado.sinLugar().stream()
                        .map(s -> PropuestaFixtureResponse.SinLugar.builder()
                                .partidoId(s.partidoId())
                                .descripcion(descripcion(porId.get(s.partidoId())))
                                .motivo(s.motivo())
                                .build())
                        .toList())
                .respetados(respetados)
                .build();
    }

    /**
     * Guarda la propuesta. Se revisa de nuevo contra la base: entre que se armó y se aplicó
     * alguien pudo haber movido un partido o cargado una reserva.
     */
    @Transactional
    public FixtureResponse aplicar(Long torneoId, AplicarFixtureRequest request) {
        Torneo torneo = buscarTorneo(torneoId);
        Map<Long, Partido> porId = partidoRepository.findDetalladosByTorneoId(torneoId).stream()
                .collect(Collectors.toMap(Partido::getId, p -> p));
        Set<Long> aplicados = new HashSet<>();
        for (AplicarFixtureRequest.Asignacion asignacion : request.getAsignaciones()) {
            Partido partido = porId.get(asignacion.getPartidoId());
            if (partido == null) {
                throw new ResourceNotFoundException("Partido", asignacion.getPartidoId());
            }
            if (!programable(partido)) {
                throw new EstadoInvalidoException(descripcion(partido) + " ya no se puede programar (se jugó o cambió).");
            }
            partido.setCancha(buscarCancha(asignacion.getCanchaId()));
            partido.setFechaHoraProgramada(asignacion.getInicio());
            aplicados.add(partido.getId());
        }
        partidoRepository.saveAll(porId.values().stream().filter(p -> aplicados.contains(p.getId())).toList());

        Contexto ctx = contexto(torneo);
        exigirSinGraves(conflictos(ctx), aplicados, "Mientras revisabas la propuesta cambió algo en las canchas o "
                + "en los partidos. Volvé a armarla. Detalle: ");
        log.info("[fixture] {} aplicó {} horarios en el torneo {}", UsuarioActual.nombre(), aplicados.size(), torneo.getNombre());
        return agenda(torneoId);
    }

    // ── Ajuste a mano ───────────────────────────────────────────────────────

    /**
     * Mueve un partido. Lo que no se puede jugar así (cancha ocupada, un jugador en dos
     * lados) se rechaza; lo dudoso (falta de descanso, una restricción) se guarda y queda
     * en la lista de conflictos para que el organizador decida.
     */
    @Transactional
    public FixtureResponse mover(Long torneoId, Long partidoId, MoverPartidoRequest request) {
        Torneo torneo = buscarTorneo(torneoId);
        Partido partido = buscarPartido(torneoId, partidoId);
        if (!SIN_JUGAR.contains(partido.getEstado())) {
            throw new EstadoInvalidoException("Ese partido ya se jugó: no se puede mover.");
        }
        partido.setCancha(buscarCancha(request.getCanchaId()));
        partido.setFechaHoraProgramada(request.getInicio());
        partidoRepository.save(partido);

        exigirSinGraves(conflictos(contexto(torneo)), Set.of(partidoId), "");
        return agenda(torneoId);
    }

    @Transactional
    public FixtureResponse desprogramar(Long torneoId, Long partidoId) {
        Partido partido = buscarPartido(torneoId, partidoId);
        if (partido.getEstado() != EstadoPartido.PENDIENTE) {
            throw new EstadoInvalidoException("Solo se le puede sacar el horario a un partido que no empezó.");
        }
        partido.setFechaHoraProgramada(null);
        partido.setCancha(null);
        partidoRepository.save(partido);
        return agenda(torneoId);
    }

    // ── Armado del contexto ─────────────────────────────────────────────────

    /** Todo lo que hace falta para programar o revisar, cargado una sola vez. */
    private static final class Contexto {
        Torneo torneo;
        int descanso;
        List<Partido> partidos;
        List<Partido> ajenos = List.of();
        List<Franja> franjas = new ArrayList<>();
        Map<Long, List<Ocupacion>> reservas = new HashMap<>();
        Map<Long, Cancha> canchas = new HashMap<>();
        Set<Long> canchasDelTorneo = new HashSet<>();
        Map<Long, ConfiguracionCategoriaTorneo> configPorCategoria = new HashMap<>();
    }

    private Contexto contexto(Torneo torneo) {
        Contexto ctx = new Contexto();
        ctx.torneo = torneo;
        ctx.descanso = descanso(torneo);
        ctx.partidos = partidoRepository.findDetalladosByTorneoId(torneo.getId());
        torneo.getConfiguracionesCategoria().stream()
                .filter(c -> c.getCategoria() != null)
                .forEach(c -> ctx.configPorCategoria.put(c.getCategoria().getId(), c));

        Set<LocalDate> dias = new LinkedHashSet<>();
        for (TorneoSede sede : torneoSedeRepository.findByTorneoIdOrderByIdAsc(torneo.getId())) {
            for (Cancha cancha : sede.getCanchas()) {
                ctx.canchas.put(cancha.getId(), cancha);
                ctx.canchasDelTorneo.add(cancha.getId());
                for (FranjaSede franja : sede.getFranjas()) {
                    ctx.franjas.add(new Franja(cancha.getId(), sede.getLugar().getId(),
                            franja.getFecha().atTime(franja.getHoraDesde()), franja.getFecha().atTime(franja.getHoraHasta())));
                    dias.add(franja.getFecha());
                }
            }
        }
        for (Partido partido : ctx.partidos) {
            if (partido.getCancha() != null) {
                ctx.canchas.putIfAbsent(partido.getCancha().getId(), partido.getCancha());
            }
            if (partido.getFechaHoraProgramada() != null && SIN_JUGAR.contains(partido.getEstado())) {
                dias.add(partido.getFechaHoraProgramada().toLocalDate());
            }
        }

        if (!dias.isEmpty() && !ctx.canchas.isEmpty()) {
            LocalDate primero = dias.stream().min(Comparator.naturalOrder()).orElseThrow();
            LocalDate ultimo = dias.stream().max(Comparator.naturalOrder()).orElseThrow();
            ctx.ajenos = partidoRepository.findDeOtrosTorneosEnCanchas(torneo.getId(), ctx.canchas.keySet(),
                    primero.atStartOfDay(), ultimo.plusDays(1).atStartOfDay());
            // En un club, la cancha también se vende por turnos: el fixture no puede pisarlos.
            if (!instalacion.esOrganizador()) {
                for (Long canchaId : ctx.canchas.keySet()) {
                    List<Ocupacion> ocupadas = new ArrayList<>();
                    dias.forEach(dia -> ocupadas.addAll(disponibilidadCanchaService.ocupacionesDelClub(canchaId, dia)));
                    if (!ocupadas.isEmpty()) {
                        ctx.reservas.put(canchaId, ocupadas);
                    }
                }
            }
        }
        return ctx;
    }

    private List<Conflicto> conflictos(Contexto ctx) {
        List<PartidoAgendado> propios = ctx.partidos.stream()
                .filter(p -> p.getFechaHoraProgramada() != null && SIN_JUGAR.contains(p.getEstado()))
                .map(p -> aAgendado(p, ctx))
                .toList();
        List<PartidoAgendado> ajenos = ctx.ajenos.stream().map(p -> aAgendado(p, ctx)).toList();
        return ConflictosFixture.revisar(propios, ajenos, ctx.franjas, ctx.reservas, ctx.canchasDelTorneo, ctx.descanso);
    }

    private void exigirSinGraves(List<Conflicto> conflictos, Set<Long> partidoIds, String prefijo) {
        conflictos.stream()
                .filter(Conflicto::grave)
                .filter(c -> c.partidoIds().stream().anyMatch(partidoIds::contains))
                .findFirst()
                .ifPresent(c -> {
                    throw new EstadoInvalidoException(prefijo + c.mensaje());
                });
    }

    // ── Conversiones ────────────────────────────────────────────────────────

    private PartidoPendiente aPendiente(Partido partido, Contexto ctx) {
        ConfiguracionCategoriaTorneo config = ctx.configPorCategoria.get(idCategoria(partido));
        List<RestriccionHoraria> restricciones = new ArrayList<>();
        restricciones.addAll(partido.getLocal().getRestricciones());
        restricciones.addAll(partido.getVisitante().getRestricciones());
        int prioridad = partido.getFase() == FasePartido.ELIMINACION
                ? 100 + (partido.getRonda() != null ? partido.getRonda().getOrden() : 0)
                : partido.getJornada() != null ? partido.getJornada() : 1;
        return new PartidoPendiente(partido.getId(), jugadores(partido).keySet(), restricciones,
                duracion(partido, config), config != null && config.getLugarFijo() != null ? config.getLugarFijo().getId() : null,
                prioridad);
    }

    private PartidoFijo aFijo(Partido partido, Contexto ctx) {
        LocalDateTime inicio = partido.getFechaHoraProgramada();
        return new PartidoFijo(partido.getCancha() != null ? partido.getCancha().getId() : null,
                lugarId(partido), jugadores(partido).keySet(), inicio, inicio.plusMinutes(duracion(partido, configDe(partido, ctx))));
    }

    private PartidoAgendado aAgendado(Partido partido, Contexto ctx) {
        ConfiguracionCategoriaTorneo config = configDe(partido, ctx);
        LocalDateTime inicio = partido.getFechaHoraProgramada();
        boolean propio = partido.getTorneo().getId().equals(ctx.torneo.getId());
        return new PartidoAgendado(partido.getId(),
                propio ? descripcion(partido) : "un partido de " + partido.getTorneo().getNombre(),
                partido.getCancha() != null ? partido.getCancha().getId() : null,
                partido.getCancha() != null ? nombreCancha(partido.getCancha()) : null,
                lugarId(partido), inicio, inicio.plusMinutes(duracion(partido, config)), jugadores(partido),
                nombrePareja(partido.getLocal()), restricciones(partido.getLocal()),
                nombrePareja(partido.getVisitante()), restricciones(partido.getVisitante()),
                propio && config != null && config.getLugarFijo() != null ? config.getLugarFijo().getId() : null,
                propio && config != null && config.getLugarFijo() != null ? config.getLugarFijo().getNombre() : null);
    }

    private FixtureResponse.PartidoFixture aPartidoFixture(Partido partido, Contexto ctx) {
        ConfiguracionCategoriaTorneo config = ctx.configPorCategoria.get(idCategoria(partido));
        int duracion = duracion(partido, config);
        Cancha cancha = partido.getCancha();
        Lugar lugar = cancha != null ? cancha.getLugar() : null;
        List<FixtureResponse.JugadorFixture> jugadores = new ArrayList<>();
        for (Pareja pareja : new Pareja[] { partido.getLocal(), partido.getVisitante() }) {
            if (pareja == null) {
                continue;
            }
            for (Jugador jugador : new Jugador[] { pareja.getJugador1(), pareja.getJugador2() }) {
                if (jugador != null) {
                    jugadores.add(FixtureResponse.JugadorFixture.builder()
                            .id(jugador.getId())
                            .nombre(nombre(jugador))
                            .telefono(jugador.getTelefono())
                            .parejaId(pareja.getId())
                            .build());
                }
            }
        }
        return FixtureResponse.PartidoFixture.builder()
                .id(partido.getId())
                .categoriaId(idCategoria(partido))
                .categoriaNombre(nombreCategoria(partido))
                .fase(partido.getFase())
                .etapa(etapa(partido))
                .estado(partido.getEstado())
                .parejaLocalId(partido.getLocal() != null ? partido.getLocal().getId() : null)
                .parejaVisitanteId(partido.getVisitante() != null ? partido.getVisitante().getId() : null)
                .local(partido.getLocal() != null ? nombrePareja(partido.getLocal()) : "A definir")
                .visitante(partido.getVisitante() != null ? nombrePareja(partido.getVisitante()) : "A definir")
                .jugadores(jugadores)
                .canchaId(cancha != null ? cancha.getId() : null)
                .canchaNombre(cancha != null ? cancha.getNombre() : null)
                .lugarId(lugar != null ? lugar.getId() : null)
                .lugarNombre(lugar != null ? lugar.getNombre() : null)
                .lugarDireccion(lugar != null ? lugar.getDireccion() : null)
                .inicio(partido.getFechaHoraProgramada())
                .fin(partido.getFechaHoraProgramada() != null ? partido.getFechaHoraProgramada().plusMinutes(duracion) : null)
                .duracionMinutos(duracion)
                .marcador(partido.getMarcador())
                .programable(programable(partido) || partido.getEstado() == EstadoPartido.EN_CURSO)
                .build();
    }

    // ── Ayudas ──────────────────────────────────────────────────────────────

    private boolean programable(Partido partido) {
        return partido.getEstado() == EstadoPartido.PENDIENTE && partido.getLocal() != null && partido.getVisitante() != null;
    }

    static int duracion(Partido partido, ConfiguracionCategoriaTorneo config) {
        if (config != null && config.getDuracionPartidoMinutos() != null) {
            return config.getDuracionPartidoMinutos();
        }
        return FormatoPorFase.resolver(partido, config).getDuracionSugeridaMinutos();
    }

    private ConfiguracionCategoriaTorneo configDe(Partido partido, Contexto ctx) {
        Long categoriaId = idCategoria(partido);
        if (partido.getTorneo().getId().equals(ctx.torneo.getId())) {
            return ctx.configPorCategoria.get(categoriaId);
        }
        return partido.getTorneo().getConfiguracionesCategoria().stream()
                .filter(c -> c.getCategoria() != null && c.getCategoria().getId().equals(categoriaId))
                .findFirst().orElse(null);
    }

    private static String duracionSugerida(ConfiguracionCategoriaTorneo config) {
        FormatoPartido general = FormatoPartido.desdeMejorDeSets(config.getMejorDeSets());
        Map<String, Integer> porFase = new LinkedHashMap<>();
        if (config.isIncluyeFaseGrupos() || !config.isIncluyeEliminacion()) {
            porFase.put("zona", Objects.requireNonNullElse(config.getFormatoPartidoGrupos(), general).getDuracionSugeridaMinutos());
        }
        if (config.isIncluyeEliminacion()) {
            FormatoPartido llave = Objects.requireNonNullElse(config.getFormatoPartidoEliminacion(), general);
            porFase.put("llave", llave.getDuracionSugeridaMinutos());
            porFase.put("finales", Objects.requireNonNullElse(config.getFormatoPartidoFinales(), llave).getDuracionSugeridaMinutos());
        }
        if (new HashSet<>(porFase.values()).size() == 1) {
            return porFase.values().iterator().next() + " min";
        }
        return porFase.entrySet().stream().map(e -> e.getKey() + " " + e.getValue() + " min").collect(Collectors.joining(" · "));
    }

    private static int descanso(Torneo torneo) {
        return torneo.getDescansoMinimoMinutos() != null ? torneo.getDescansoMinimoMinutos() : DESCANSO_POR_DEFECTO;
    }

    private static Map<Long, String> jugadores(Partido partido) {
        Map<Long, String> jugadores = new LinkedHashMap<>();
        for (Pareja pareja : new Pareja[] { partido.getLocal(), partido.getVisitante() }) {
            if (pareja == null) {
                continue;
            }
            for (Jugador jugador : new Jugador[] { pareja.getJugador1(), pareja.getJugador2() }) {
                if (jugador != null) {
                    jugadores.put(jugador.getId(), nombre(jugador));
                }
            }
        }
        return jugadores;
    }

    private static List<RestriccionHoraria> restricciones(Pareja pareja) {
        return pareja != null ? pareja.getRestricciones() : List.of();
    }

    private static Long lugarId(Partido partido) {
        return partido.getCancha() != null && partido.getCancha().getLugar() != null ? partido.getCancha().getLugar().getId() : null;
    }

    static Categoria categoria(Partido partido) {
        if (partido.getLocal() != null && partido.getLocal().getCategoria() != null) {
            return partido.getLocal().getCategoria();
        }
        if (partido.getVisitante() != null && partido.getVisitante().getCategoria() != null) {
            return partido.getVisitante().getCategoria();
        }
        if (partido.getGrupo() != null && partido.getGrupo().getCategoria() != null) {
            return partido.getGrupo().getCategoria();
        }
        return partido.getRonda() != null ? partido.getRonda().getCategoria() : null;
    }

    private static Long idCategoria(Partido partido) {
        Categoria categoria = categoria(partido);
        return categoria != null ? categoria.getId() : null;
    }

    private static String nombreCategoria(Partido partido) {
        Categoria categoria = categoria(partido);
        return categoria != null ? categoria.getNombre() : null;
    }

    /** "Quinta · Pérez/Gómez vs Díaz/Ruiz": lo que se lee en un conflicto o una propuesta. */
    static String descripcion(Partido partido) {
        String categoria = nombreCategoria(partido);
        String cruce = (partido.getLocal() != null ? nombrePareja(partido.getLocal()) : "A definir") + " vs "
                + (partido.getVisitante() != null ? nombrePareja(partido.getVisitante()) : "A definir");
        return categoria != null ? categoria + " · " + cruce : cruce;
    }

    private static String etapa(Partido partido) {
        if (partido.getFase() == FasePartido.ELIMINACION) {
            return partido.getRonda() != null ? partido.getRonda().getNombre() : "Llave";
        }
        String grupo = partido.getGrupo() != null ? partido.getGrupo().getNombre() : "Zona";
        return partido.getJornada() != null ? grupo + " · fecha " + partido.getJornada() : grupo;
    }

    static String nombrePareja(Pareja pareja) {
        return apellido(pareja.getJugador1()) + "/" + apellido(pareja.getJugador2());
    }

    private static String apellido(Jugador jugador) {
        return jugador == null ? "?" : jugador.getApellido();
    }

    private static String nombre(Jugador jugador) {
        return (jugador.getNombre() + " " + jugador.getApellido()).trim();
    }

    private static String nombreCancha(Cancha cancha) {
        return cancha.getLugar() != null ? cancha.getNombre() + " (" + cancha.getLugar().getNombre() + ")" : cancha.getNombre();
    }

    private Torneo buscarTorneo(Long torneoId) {
        return torneoRepository.findById(torneoId).orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));
    }

    private Partido buscarPartido(Long torneoId, Long partidoId) {
        Partido partido = partidoRepository.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", partidoId));
        if (!partido.getTorneo().getId().equals(torneoId)) {
            throw new ResourceNotFoundException("Partido", partidoId);
        }
        return partido;
    }

    private Cancha buscarCancha(Long canchaId) {
        return canchaRepository.findById(canchaId).orElseThrow(() -> new ResourceNotFoundException("Cancha", canchaId));
    }
}
