package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.CuentaInscripcionesResponse;
import com.padel.rankpadel.dto.response.FinanzasTorneoResponse;
import com.padel.rankpadel.dto.response.FixtureResponse;
import com.padel.rankpadel.dto.response.PanelOrganizadorResponse;
import com.padel.rankpadel.dto.response.PanelOrganizadorResponse.Alerta;
import com.padel.rankpadel.dto.response.PanelOrganizadorResponse.CategoriaDemanda;
import com.padel.rankpadel.dto.response.PanelOrganizadorResponse.CupoCategoria;
import com.padel.rankpadel.dto.response.PanelOrganizadorResponse.TorneoEnMarcha;
import com.padel.rankpadel.dto.response.PanelOrganizadorResponse.TorneoTerminado;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.ConfiguracionCategoriaTorneo;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.entity.TorneoSede;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoSolicitud;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.repository.ParejaRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.SolicitudInscripcionRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.repository.TorneoSedeRepository;
import com.padel.rankpadel.util.Pesos;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * El panel del organizador. No calcula nada propio: junta lo que ya saben el fixture, los
 * cobros y las finanzas de cada torneo, y lo convierte en lo que hay que mirar hoy.
 */
@Service
@RequiredArgsConstructor
public class PanelOrganizadorService {

    private static final List<EstadoTorneo> EN_MARCHA = List.of(
            EstadoTorneo.BORRADOR, EstadoTorneo.INSCRIPCION, EstadoTorneo.SORTEADO, EstadoTorneo.EN_CURSO);
    private static final int TERMINADOS_A_MOSTRAR = 8;
    /** Cuántos días antes del torneo empieza a alarmar la deuda de inscripción. */
    private static final int DIAS_DE_AVISO_DEUDA = 3;
    /** Un partido que debía empezar hace más que esto y no tiene resultado. */
    private static final int HORAS_SIN_RESULTADO = 3;

    private final TorneoRepository torneoRepository;
    private final TorneoSedeRepository torneoSedeRepository;
    private final PartidoRepository partidoRepository;
    private final ParejaRepository parejaRepository;
    private final SolicitudInscripcionRepository solicitudInscripcionRepository;
    private final FixtureService fixtureService;
    private final CobroInscripcionService cobroInscripcionService;
    private final FinanzasTorneoService finanzasTorneoService;

    @Transactional(readOnly = true)
    public PanelOrganizadorResponse panel() {
        boolean duenio = UsuarioActual.esDuenio();
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        List<Torneo> enMarcha = torneoRepository.findByActivoTrueAndEstadoIn(EN_MARCHA).stream()
                .sorted(Comparator.comparing(Torneo::getFechaInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<Torneo> terminados = torneoRepository.findByActivoTrueAndEstadoIn(List.of(EstadoTorneo.FINALIZADO)).stream()
                .sorted(Comparator.comparing(Torneo::getFechaFin, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TERMINADOS_A_MOSTRAR)
                .toList();

        List<Long> ids = new ArrayList<>();
        enMarcha.forEach(t -> ids.add(t.getId()));
        terminados.forEach(t -> ids.add(t.getId()));
        Map<Long, Map<Long, Object[]>> parejasPorTorneo = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] fila : parejaRepository.contarPorTorneoYCategoria(ids)) {
                parejasPorTorneo.computeIfAbsent((Long) fila[0], k -> new LinkedHashMap<>()).put((Long) fila[1], fila);
            }
        }

        List<Alerta> alertas = new ArrayList<>();
        List<TorneoEnMarcha> torneos = new ArrayList<>();
        int partidosHoy = 0;
        int enJuego = 0;
        int jugadosHoy = 0;

        for (Torneo torneo : enMarcha) {
            Map<Long, Object[]> conteo = parejasPorTorneo.getOrDefault(torneo.getId(), Map.of());
            List<TorneoSede> sedes = torneoSedeRepository.findByTorneoIdOrderByIdAsc(torneo.getId());
            int pendientes = solicitudInscripcionRepository.findByTorneoIdAndEstado(torneo.getId(), EstadoSolicitud.PENDIENTE).size();

            int sinHorario = 0;
            int choques = 0;
            if (torneo.getEstado() == EstadoTorneo.SORTEADO || torneo.getEstado() == EstadoTorneo.EN_CURSO) {
                FixtureResponse fixture = fixtureService.agenda(torneo.getId());
                sinHorario = fixture.getSinProgramar();
                choques = (int) fixture.getConflictos().stream().filter(FixtureResponse.Conflicto::isGrave).count();
            }

            long sinResultado = 0;
            for (Partido partido : partidoRepository.findByTorneoId(torneo.getId())) {
                LocalDateTime inicio = partido.getFechaHoraProgramada();
                if (inicio == null || partido.getEstado() == EstadoPartido.BYE) {
                    continue;
                }
                boolean sinJugar = partido.getEstado() == EstadoPartido.PENDIENTE || partido.getEstado() == EstadoPartido.EN_CURSO;
                if (sinJugar && inicio.isBefore(ahora.minusHours(HORAS_SIN_RESULTADO))) {
                    sinResultado++;
                }
                if (inicio.toLocalDate().equals(hoy)) {
                    partidosHoy++;
                    if (partido.getEstado() == EstadoPartido.EN_CURSO) {
                        enJuego++;
                    } else if (!sinJugar) {
                        jugadosHoy++;
                    }
                }
            }

            CuentaInscripcionesResponse cuenta = cobroInscripcionService.cuenta(torneo.getId());
            FinanzasTorneoResponse.Balance balance = duenio ? finanzasTorneoService.balance(torneo.getId()) : null;
            BigDecimal deudaClubes = duenio ? balance.getClubesAcordado().subtract(balance.getClubesPagado()) : null;

            torneos.add(TorneoEnMarcha.builder()
                    .id(torneo.getId())
                    .nombre(torneo.getNombre())
                    .estado(torneo.getEstado())
                    .fechaInicio(torneo.getFechaInicio())
                    .fechaFin(torneo.getFechaFin())
                    .clubes(sedes.size())
                    .categorias(cupos(torneo, conteo))
                    .parejas(conteo.values().stream().mapToInt(f -> ((Long) f[3]).intValue()).sum())
                    .solicitudesPendientes(pendientes)
                    .partidosSinHorario(sinHorario)
                    .choques(choques)
                    .jugadoresQueDeben(cuenta.getJugadoresConDeuda())
                    .inscripcionesCobrado(duenio ? cuenta.getTotalCobrado() : null)
                    .inscripcionesPendiente(duenio ? cuenta.getTotalPendiente() : null)
                    .deudaClubes(deudaClubes)
                    .resultadoProyectado(duenio ? balance.getResultadoProyectado() : null)
                    .build());

            String nombre = torneo.getNombre();
            if (choques > 0) {
                alertas.add(alerta("ALTA", nombre + ": " + choques + (choques == 1 ? " choque" : " choques")
                        + " en los horarios (cancha o jugador en dos lados).", torneo, "horarios"));
            }
            boolean empiezaPronto = torneo.getFechaInicio() != null && !torneo.getFechaInicio().isAfter(hoy.plusDays(DIAS_DE_AVISO_DEUDA));
            if (cuenta.getJugadoresConDeuda() > 0 && empiezaPronto) {
                alertas.add(alerta("ALTA", nombre + ": " + cuenta.getJugadoresConDeuda()
                        + (cuenta.getJugadoresConDeuda() == 1 ? " jugador debe" : " jugadores deben") + " la inscripción"
                        + (duenio ? " (" + Pesos.formatear(cuenta.getTotalPendiente()) + ")" : "") + ".", torneo, "cobros"));
            }
            if (sinResultado > 0) {
                alertas.add(alerta("MEDIA", nombre + ": " + sinResultado + (sinResultado == 1 ? " partido ya debería" : " partidos ya deberían")
                        + " haberse jugado y no tiene resultado.", torneo, "partidos"));
            }
            if (sinHorario > 0) {
                alertas.add(alerta("MEDIA", nombre + ": " + sinHorario + (sinHorario == 1 ? " partido listo para jugar no tiene" : " partidos listos para jugar no tienen")
                        + " horario.", torneo, "horarios"));
            }
            if (pendientes > 0) {
                alertas.add(alerta("MEDIA", nombre + ": " + pendientes + (pendientes == 1 ? " inscripción espera" : " inscripciones esperan")
                        + " aprobación.", torneo, "inscripciones"));
            }
            long clubesSinHorarios = sedes.stream().filter(s -> s.getFranjas().isEmpty()).count();
            if (clubesSinHorarios > 0 && torneo.getEstado() != EstadoTorneo.EN_CURSO) {
                alertas.add(alerta("MEDIA", nombre + ": " + (clubesSinHorarios == 1 ? "un club no tiene" : clubesSinHorarios + " clubes no tienen")
                        + " cargados los días y horarios que cede.", torneo, "sedes"));
            }
        }

        List<TorneoTerminado> resultados = new ArrayList<>();
        for (Torneo torneo : terminados) {
            FinanzasTorneoResponse.Balance balance = duenio ? finanzasTorneoService.balance(torneo.getId()) : null;
            int parejas = parejasPorTorneo.getOrDefault(torneo.getId(), Map.of()).values().stream()
                    .mapToInt(f -> ((Long) f[3]).intValue()).sum();
            resultados.add(TorneoTerminado.builder()
                    .id(torneo.getId())
                    .nombre(torneo.getNombre())
                    .fechaFin(torneo.getFechaFin())
                    .parejas(parejas)
                    .resultadoProyectado(duenio ? balance.getResultadoProyectado() : null)
                    .resultadoReal(duenio ? balance.getResultadoReal() : null)
                    .porCobrar(duenio ? balance.getPorCobrar() : null)
                    .porPagar(duenio ? balance.getPorPagar() : null)
                    .build());
            if (duenio && balance.getPorPagar().signum() > 0) {
                alertas.add(alerta("ALTA", torneo.getNombre() + " terminó y quedan " + Pesos.formatear(balance.getPorPagar())
                        + " por pagar (clubes o premios).", torneo, "finanzas"));
            }
            if (duenio && balance.getPorCobrar().signum() > 0) {
                alertas.add(alerta("MEDIA", torneo.getNombre() + " terminó y quedan " + Pesos.formatear(balance.getPorCobrar())
                        + " sin cobrar (inscripciones o sponsors).", torneo, "finanzas"));
            }
        }

        alertas.sort(Comparator.comparing(a -> "ALTA".equals(a.getNivel()) ? 0 : 1));
        return PanelOrganizadorResponse.builder()
                .hoy(PanelOrganizadorResponse.Hoy.builder().fecha(hoy).partidos(partidosHoy).enJuego(enJuego).jugados(jugadosHoy).build())
                .alertas(alertas)
                .torneos(torneos)
                .terminados(resultados)
                .categorias(demanda(enMarcha, terminados, parejasPorTorneo))
                .build();
    }

    private static List<CupoCategoria> cupos(Torneo torneo, Map<Long, Object[]> conteo) {
        List<CupoCategoria> cupos = new ArrayList<>();
        for (Categoria categoria : torneo.getCategorias().stream().sorted(Comparator.comparing(Categoria::getNombre)).toList()) {
            Object[] fila = conteo.get(categoria.getId());
            cupos.add(CupoCategoria.builder()
                    .categoria(categoria.getNombre())
                    .inscriptas(fila != null ? ((Long) fila[3]).intValue() : 0)
                    .cupo(cupo(torneo, categoria.getId()))
                    .build());
        }
        return cupos;
    }

    static Integer cupo(Torneo torneo, Long categoriaId) {
        Integer deConfig = torneo.getConfiguracionesCategoria().stream()
                .filter(c -> c.getCategoria() != null && c.getCategoria().getId().equals(categoriaId))
                .map(ConfiguracionCategoriaTorneo::getCupo)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if (deConfig != null) {
            return deConfig;
        }
        return torneo.getCuposPorCategoria() != null ? torneo.getCuposPorCategoria().get(categoriaId) : null;
    }

    /** Qué categorías llenan: parejas inscriptas en los torneos en marcha y los últimos terminados. */
    private static List<CategoriaDemanda> demanda(List<Torneo> enMarcha, List<Torneo> terminados,
            Map<Long, Map<Long, Object[]>> parejasPorTorneo) {
        Map<String, int[]> porCategoria = new LinkedHashMap<>();
        List<Torneo> todos = new ArrayList<>(enMarcha);
        todos.addAll(terminados);
        for (Torneo torneo : todos) {
            for (Object[] fila : parejasPorTorneo.getOrDefault(torneo.getId(), Map.of()).values()) {
                int[] acumulado = porCategoria.computeIfAbsent((String) fila[2], k -> new int[4]);
                int inscriptas = ((Long) fila[3]).intValue();
                acumulado[0] += inscriptas;
                acumulado[1]++;
                Integer cupo = cupo(torneo, (Long) fila[1]);
                if (cupo != null && cupo > 0) {
                    acumulado[2] += inscriptas;
                    acumulado[3] += cupo;
                }
            }
        }
        return porCategoria.entrySet().stream()
                .map(e -> CategoriaDemanda.builder()
                        .categoria(e.getKey())
                        .parejas(e.getValue()[0])
                        .torneos(e.getValue()[1])
                        .ocupacionPorcentaje(e.getValue()[3] > 0 ? Math.round(100f * e.getValue()[2] / e.getValue()[3]) : null)
                        .build())
                .sorted(Comparator.comparingInt(CategoriaDemanda::getParejas).reversed())
                .toList();
    }

    private static Alerta alerta(String nivel, String mensaje, Torneo torneo, String vista) {
        return Alerta.builder().nivel(nivel).mensaje(mensaje).torneoId(torneo.getId()).vista(vista).build();
    }
}
