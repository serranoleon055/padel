package com.padel.rankpadel.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.padel.rankpadel.util.BracketSeeder;
import com.padel.rankpadel.util.PosicionGrupoOrdenador;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.ResultadoRequest;
import com.padel.rankpadel.dto.response.PartidoResponse;
import com.padel.rankpadel.entity.Grupo;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.PosicionGrupo;
import com.padel.rankpadel.entity.RondaEliminatorias;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.enums.EstadoTorneo;
import com.padel.rankpadel.enums.FasePartido;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.PartidoMapper;
import com.padel.rankpadel.repository.GrupoRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.PosicionGrupoRepository;
import com.padel.rankpadel.repository.RondaEliminatoriasRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResultadoService {

    private final PartidoRepository partidoRepository;
    private final PosicionGrupoRepository posicionGrupoRepository;
    private final PartidoMapper partidoMapper;
    private final TorneoRepository torneoRepository;
    private final RondaEliminatoriasRepository rondaEliminatoriasRepository;
    private final GrupoRepository grupoRepository;
    private final RankingService rankingService;
    private final CampeonService campeonService;

    @Transactional
    public PartidoResponse cargarResultado(Long torneoId, Long partidoId, ResultadoRequest request) {

        Partido partido = partidoRepository.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", partidoId));

        if (!partido.getTorneo().getId().equals(torneoId)) {
            throw new EstadoInvalidoException("El partido no pertenece al torneo indicado");
        }

        if (!partido.getEstado().equals(EstadoPartido.PENDIENTE) && !partido.getEstado().equals(EstadoPartido.EN_CURSO)) {
            throw new EstadoInvalidoException("El partido debe estar en estado PENDIENTE o EN_CURSO para cargar resultado");
        }

        Torneo torneo = partido.getTorneo();
        if (!torneo.getEstado().equals(EstadoTorneo.EN_CURSO)) {
            throw new EstadoInvalidoException("El torneo debe estar iniciado para cargar resultados");
        }

        aplicarResultado(partido, request.getMarcador());

        return partidoMapper.partidoToResponse(partido);
    }

    @Transactional
    public PartidoResponse corregirResultado(Long torneoId, Long partidoId, ResultadoRequest request) {
        Partido partido = partidoRepository.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", partidoId));

        if (!partido.getTorneo().getId().equals(torneoId)) {
            throw new EstadoInvalidoException("El partido no pertenece al torneo indicado");
        }

        if (!partido.getTorneo().getEstado().equals(EstadoTorneo.EN_CURSO)) {
            throw new EstadoInvalidoException("El torneo debe estar iniciado para corregir resultados");
        }

        if (!partido.getEstado().equals(EstadoPartido.FINALIZADO)) {
            throw new EstadoInvalidoException("Solo se puede corregir un partido con resultado cargado (FINALIZADO)");
        }

        validarCorreccionSinConsecuencias(partido);

        if (partido.getFase().equals(FasePartido.GRUPOS)) {
            revertirPosicionesGrupo(partido);
        }
        rankingService.revertirRankingPartido(partido);

        aplicarResultado(partido, request.getMarcador());

        return partidoMapper.partidoToResponse(partido);
    }

    private void aplicarResultado(Partido partido, String marcador) {
        Pareja ganador = determinarGanador(partido, marcador);
        partido.setMarcador(marcador);
        partido.setGanador(ganador);
        partido.setEstado(EstadoPartido.FINALIZADO);
        if (partido.getFechaHora() == null) {
            partido.setFechaHora(LocalDateTime.now());
        }
        partidoRepository.save(partido);

        rankingService.actualizarRanking(partido);

        if (partido.getFase().equals(FasePartido.GRUPOS)) {
            actualizarPosicionesGrupo(partido);
            verificarAvanceEliminatorias(partido.getGrupo().getId(), partido.getTorneo());
        } else if (partido.getFase().equals(FasePartido.ELIMINACION)) {
            avanzarBracketSiCorresponde(partido);
        }
    }

    private void validarCorreccionSinConsecuencias(Partido partido) {
        if (partido.getFase().equals(FasePartido.GRUPOS)) {
            boolean cuadroGenerado = !rondaEliminatoriasRepository
                    .findByTorneoIdAndCategoriaIdOrderByOrden(partido.getTorneo().getId(),
                            partido.getGrupo().getCategoria().getId())
                    .isEmpty();
            if (cuadroGenerado) {
                throw new EstadoInvalidoException(
                        "No se puede corregir: el cuadro de esta categoría ya fue generado. Reabrí el torneo para rehacerlo.");
            }
        } else if (partido.getFase().equals(FasePartido.ELIMINACION)) {
            int ordenActual = partido.getRonda().getOrden();
            boolean hayRondaPosterior = rondaEliminatoriasRepository
                    .findByTorneoIdAndCategoriaIdOrderByOrden(partido.getTorneo().getId(),
                            partido.getRonda().getCategoria().getId())
                    .stream()
                    .anyMatch(ronda -> ronda.getOrden() > ordenActual);
            if (hayRondaPosterior) {
                throw new EstadoInvalidoException(
                        "No se puede corregir: ya se generó la ronda siguiente. Reabrí el torneo para rehacerla.");
            }
        }
    }

    private void revertirPosicionesGrupo(Partido partido) {
        PosicionGrupo posLocal = posicionGrupoRepository
                .findByGrupoIdAndParejaId(partido.getGrupo().getId(), partido.getLocal().getId())
                .orElseThrow(() -> new ResourceNotFoundException("PosicionGrupo local", partido.getLocal().getId()));
        PosicionGrupo posVisitante = posicionGrupoRepository
                .findByGrupoIdAndParejaId(partido.getGrupo().getId(), partido.getVisitante().getId())
                .orElseThrow(() -> new ResourceNotFoundException("PosicionGrupo visitante", partido.getVisitante().getId()));

        posLocal.setPj(posLocal.getPj() - 1);
        posVisitante.setPj(posVisitante.getPj() - 1);

        int[] sets = contarSets(partido.getMarcador());
        int setsLocal = sets[0];
        int setsVisitante = sets[1];

        posLocal.setSetsGanados(posLocal.getSetsGanados() - setsLocal);
        posLocal.setSetsPerdidos(posLocal.getSetsPerdidos() - setsVisitante);
        posVisitante.setSetsGanados(posVisitante.getSetsGanados() - setsVisitante);
        posVisitante.setSetsPerdidos(posVisitante.getSetsPerdidos() - setsLocal);

        int[] juegos = contarJuegos(partido.getMarcador());
        posLocal.setJuegosGanados(posLocal.getJuegosGanados() - juegos[0]);
        posLocal.setJuegosPerdidos(posLocal.getJuegosPerdidos() - juegos[1]);
        posVisitante.setJuegosGanados(posVisitante.getJuegosGanados() - juegos[1]);
        posVisitante.setJuegosPerdidos(posVisitante.getJuegosPerdidos() - juegos[0]);

        if (partido.getGanador().getId().equals(partido.getLocal().getId())) {
            posLocal.setPg(posLocal.getPg() - 1);
            posLocal.setPuntos(posLocal.getPuntos() - 3);
            posVisitante.setPp(posVisitante.getPp() - 1);
        } else {
            posVisitante.setPg(posVisitante.getPg() - 1);
            posVisitante.setPuntos(posVisitante.getPuntos() - 3);
            posLocal.setPp(posLocal.getPp() - 1);
        }

        posicionGrupoRepository.save(posLocal);
        posicionGrupoRepository.save(posVisitante);
    }

    private Pareja determinarGanador(Partido partido, String marcador) {
        if (!marcador.matches("\\d+-\\d+(\\s*/\\s*\\d+-\\d+)*")) {
            throw new EstadoInvalidoException("El marcador debe tener formato 6-3 / 4-6 / 7-5 o 6-3 / 4-6 / 10-8 (super tie-break)");
        }

        int mejorDe = partido.getTorneo().getMejorDeSets() != null ? partido.getTorneo().getMejorDeSets() : 3;
        int setsParaGanar = (mejorDe / 2) + 1;

        String[] sets = marcador.split("\\s*/\\s*");
        if (sets.length < setsParaGanar || sets.length > mejorDe) {
            throw new EstadoInvalidoException(mejorDe == 1
                    ? "Este torneo es a 1 set: cargá un único set (por ejemplo 6-3)"
                    : "Un partido de pádel se define al mejor de " + mejorDe + " sets: cargá entre "
                            + setsParaGanar + " y " + mejorDe + " sets");
        }

        int setsLocal = 0;
        int setsVisitante = 0;

        for (int i = 0; i < sets.length; i++) {
            String[] games = sets[i].trim().split("-");
            int gamesLocal = Integer.parseInt(games[0].trim());
            int gamesVisitante = Integer.parseInt(games[1].trim());
            if (gamesLocal == gamesVisitante) {
                throw new EstadoInvalidoException("Un set no puede terminar empatado");
            }
            boolean esSupertiebreak = mejorDe > 1 && (i == mejorDe - 1) && (Math.max(gamesLocal, gamesVisitante) >= 10);
            validarSet(gamesLocal, gamesVisitante, esSupertiebreak, i + 1);
            if (gamesLocal > gamesVisitante) {
                setsLocal++;
            } else {
                setsVisitante++;
            }
            if ((setsLocal == setsParaGanar || setsVisitante == setsParaGanar) && i < sets.length - 1) {
                throw new EstadoInvalidoException("El partido ya quedó definido; no puede tener más sets");
            }
        }

        if (setsLocal != setsParaGanar && setsVisitante != setsParaGanar) {
            throw new EstadoInvalidoException("El partido no está definido: el ganador debe ganar " + setsParaGanar
                    + (setsParaGanar == 1 ? " set" : " sets"));
        }

        return setsLocal > setsVisitante ? partido.getLocal() : partido.getVisitante();
    }

    private void validarSet(int local, int visitante, boolean esSupertiebreak, int numSet) {
        int ganador = Math.max(local, visitante);
        int perdedor = Math.min(local, visitante);
        int diferencia = ganador - perdedor;

        if (esSupertiebreak) {
            if (ganador < 10) {
                throw new EstadoInvalidoException("El super tie-break (set " + numSet + ") debe jugarse hasta al menos 10: " + local + "-" + visitante);
            }
            if (diferencia < 2) {
                throw new EstadoInvalidoException("El super tie-break (set " + numSet + ") requiere 2 puntos de ventaja: " + local + "-" + visitante);
            }
        } else {
            if (ganador < 6) {
                throw new EstadoInvalidoException("El set " + numSet + " no tiene un marcador válido (mínimo 6 juegos): " + local + "-" + visitante);
            }
            if (ganador == 6 && diferencia < 2) {
                throw new EstadoInvalidoException("El set " + numSet + " con " + local + "-" + visitante + " no es válido: se necesita ventaja de 2 o llegar a 7");
            }
            if (ganador == 7 && perdedor < 5) {
                throw new EstadoInvalidoException("El set " + numSet + " con " + local + "-" + visitante + " no es un marcador válido de pádel");
            }
            if (ganador > 7) {
                throw new EstadoInvalidoException("Un set normal no puede superar 7 juegos. Use super tie-break para el 3er set: " + local + "-" + visitante);
            }
        }
    }

    private void actualizarPosicionesGrupo(Partido partido) {
        PosicionGrupo posLocal = posicionGrupoRepository
                .findByGrupoIdAndParejaId(partido.getGrupo().getId(), partido.getLocal().getId())
                .orElseThrow(() -> new ResourceNotFoundException("PosicionGrupo local", partido.getLocal().getId()));

        PosicionGrupo posVisitante = posicionGrupoRepository
                .findByGrupoIdAndParejaId(partido.getGrupo().getId(), partido.getVisitante().getId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("PosicionGrupo visitante", partido.getVisitante().getId()));

        posLocal.setPj(posLocal.getPj() + 1);
        posVisitante.setPj(posVisitante.getPj() + 1);

        int[] sets = contarSets(partido.getMarcador());
        int setsLocal = sets[0];
        int setsVisitante = sets[1];

        posLocal.setSetsGanados(posLocal.getSetsGanados() + setsLocal);
        posLocal.setSetsPerdidos(posLocal.getSetsPerdidos() + setsVisitante);
        posVisitante.setSetsGanados(posVisitante.getSetsGanados() + setsVisitante);
        posVisitante.setSetsPerdidos(posVisitante.getSetsPerdidos() + setsLocal);

        int[] juegos = contarJuegos(partido.getMarcador());
        posLocal.setJuegosGanados(posLocal.getJuegosGanados() + juegos[0]);
        posLocal.setJuegosPerdidos(posLocal.getJuegosPerdidos() + juegos[1]);
        posVisitante.setJuegosGanados(posVisitante.getJuegosGanados() + juegos[1]);
        posVisitante.setJuegosPerdidos(posVisitante.getJuegosPerdidos() + juegos[0]);

        if (partido.getGanador().getId().equals(partido.getLocal().getId())) {
            posLocal.setPg(posLocal.getPg() + 1);
            posLocal.setPuntos(posLocal.getPuntos() + 3);
            posVisitante.setPp(posVisitante.getPp() + 1);
        } else {
            posVisitante.setPg(posVisitante.getPg() + 1);
            posVisitante.setPuntos(posVisitante.getPuntos() + 3);
            posLocal.setPp(posLocal.getPp() + 1);
        }

        posicionGrupoRepository.save(posLocal);
        posicionGrupoRepository.save(posVisitante);
    }

    private int[] contarJuegos(String marcador) {
        if (marcador == null || marcador.isBlank()) return new int[]{0, 0};
        String[] sets = marcador.split("\\s*/\\s*");
        int juegosLocal = 0, juegosVisitante = 0;
        for (String set : sets) {
            String[] games = set.trim().split("-");
            if (games.length < 2) continue;
            try {
                juegosLocal += Integer.parseInt(games[0].trim());
                juegosVisitante += Integer.parseInt(games[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return new int[]{juegosLocal, juegosVisitante};
    }

    private int[] contarSets(String marcador) {
        if (marcador == null || marcador.isBlank()) return new int[]{0, 0};
        String[] sets = marcador.split("\\s*/\\s*");
        int setsLocal = 0, setsVisitante = 0;
        for (String set : sets) {
            String[] games = set.trim().split("-");
            if (games.length < 2) continue;
            try {
                int gLocal = Integer.parseInt(games[0].trim());
                int gVis = Integer.parseInt(games[1].trim());
                if (gLocal > gVis) setsLocal++;
                else if (gVis > gLocal) setsVisitante++;
            } catch (NumberFormatException ignored) {
            }
        }
        return new int[]{setsLocal, setsVisitante};
    }

    private void verificarAvanceEliminatorias(Long grupoId, Torneo torneo) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo", grupoId));

        long pendientesGrupo = partidoRepository.findByGrupoId(grupoId).stream()
                .filter(p -> p.getEstado() == EstadoPartido.PENDIENTE || p.getEstado() == EstadoPartido.EN_CURSO)
                .count();

        if (pendientesGrupo > 0) return;

        List<Grupo> gruposCategoria = grupoRepository.findByTorneoIdAndCategoriaId(torneo.getId(), grupo.getCategoria().getId());
        boolean todosFinalizados = gruposCategoria.stream().allMatch(g -> {
            long pends = partidoRepository.findByGrupoId(g.getId()).stream()
                    .filter(p -> p.getEstado() == EstadoPartido.PENDIENTE || p.getEstado() == EstadoPartido.EN_CURSO)
                    .count();
            return pends == 0;
        });

        if (!todosFinalizados) return;

        int avanzan = torneo.getAvanzanPorGrupo() != null ? torneo.getAvanzanPorGrupo() : 1;

        Map<Long, List<PosicionGrupo>> ordenadasPorGrupo = new LinkedHashMap<>();
        for (Grupo g : gruposCategoria) {
            ordenadasPorGrupo.put(g.getId(),
                    PosicionGrupoOrdenador.ordenar(posicionGrupoRepository.findByGrupoId(g.getId())));
        }

        if (!torneo.isIncluyeEliminacion()) {
            verificarFinLiga(torneo);
            return;
        }

        boolean cuadroYaGenerado = !rondaEliminatoriasRepository
                .findByTorneoIdAndCategoriaIdOrderByOrden(torneo.getId(), grupo.getCategoria().getId())
                .isEmpty();
        if (cuadroYaGenerado) {
            return;
        }

        List<Pareja> seeds = new ArrayList<>();
        for (int tier = 0; tier < avanzan; tier++) {
            List<PosicionGrupo> tierList = new ArrayList<>();
            for (Grupo g : gruposCategoria) {
                List<PosicionGrupo> ord = ordenadasPorGrupo.get(g.getId());
                if (tier < ord.size()) tierList.add(ord.get(tier));
            }
            for (PosicionGrupo pg : PosicionGrupoOrdenador.ordenar(tierList)) {
                seeds.add(pg.getPareja());
            }
        }

        if (seeds.size() < 2) return;

        int tamano = 1;
        while (tamano < seeds.size()) tamano *= 2;

        RondaEliminatorias primeraRonda = RondaEliminatorias.builder()
                .nombre(nombreRonda(tamano))
                .orden(1)
                .torneo(torneo)
                .categoria(grupo.getCategoria())
                .build();
        rondaEliminatoriasRepository.save(primeraRonda);

        List<BracketSeeder.Match> llave = BracketSeeder.sembrar(seeds);
        partidoRepository.saveAll(BracketSeeder.construirPartidos(llave, torneo, primeraRonda));
    }

    private void avanzarBracketSiCorresponde(Partido partido) {
        Long rondaId = partido.getRonda().getId();

        long sinFinalizar = partidoRepository.findByRondaId(rondaId).stream()
                .filter(p -> p.getEstado() == EstadoPartido.PENDIENTE || p.getEstado() == EstadoPartido.EN_CURSO)
                .count();

        if (sinFinalizar > 0) return;

        List<Partido> partidosRonda = partidoRepository.findByRondaIdOrderByOrdenLlaveAscIdAsc(rondaId);
        List<Pareja> ganadores = partidosRonda.stream()
                .map(Partido::getGanador)
                .filter(g -> g != null)
                .collect(Collectors.toList());

        if (ganadores.size() == 1) {
            Torneo torneo = partido.getTorneo();
            boolean quedanPartidosPorJugar = partidoRepository.findByTorneoId(torneo.getId()).stream()
                    .anyMatch(p -> p.getEstado() == EstadoPartido.PENDIENTE
                            || p.getEstado() == EstadoPartido.EN_CURSO);
            if (!quedanPartidosPorJugar) {
                torneo.setEstado(EstadoTorneo.FINALIZADO);
                torneoRepository.save(torneo);
                rankingService.cerrarTorneo(torneo.getId());
            }
            // La final de esta categoría ya se definió: registrar campeón aunque
            // el torneo siga en curso para otras categorías.
            campeonService.recalcularCampeones(torneo);
            return;
        }

        int nuevoOrden = partido.getRonda().getOrden() + 1;
        int nuevoTamano = ganadores.size();

        RondaEliminatorias siguienteRonda = RondaEliminatorias.builder()
                .nombre(nombreRonda(nuevoTamano))
                .orden(nuevoOrden)
                .torneo(partido.getTorneo())
                .categoria(partido.getRonda().getCategoria())
                .build();
        rondaEliminatoriasRepository.save(siguienteRonda);

        List<Partido> nuevosPartidos = new ArrayList<>();
        int orden = 0;
        for (int i = 0; i + 1 < ganadores.size(); i += 2) {
            nuevosPartidos.add(Partido.builder()
                    .torneo(partido.getTorneo())
                    .local(ganadores.get(i))
                    .visitante(ganadores.get(i + 1))
                    .ronda(siguienteRonda)
                    .estado(EstadoPartido.PENDIENTE)
                    .fase(FasePartido.ELIMINACION)
                    .ordenLlave(orden++)
                    .build());
        }
        partidoRepository.saveAll(nuevosPartidos);
    }

    private void verificarFinLiga(Torneo torneo) {
        Torneo torneoActual = torneoRepository.findById(torneo.getId()).orElse(torneo);
        if (torneoActual.getEstado().equals(EstadoTorneo.FINALIZADO)) return;

        List<Partido> todosPartidos = partidoRepository.findByTorneoId(torneo.getId());
        boolean todosFin = !todosPartidos.isEmpty() && todosPartidos.stream().allMatch(p ->
            p.getEstado().equals(EstadoPartido.FINALIZADO)
            || p.getEstado().equals(EstadoPartido.BYE)
            || p.getEstado().equals(EstadoPartido.WALKOVER)
            || p.getEstado().equals(EstadoPartido.RETIRO));

        if (todosFin) {
            torneoActual.setEstado(EstadoTorneo.FINALIZADO);
            torneoRepository.save(torneoActual);
            rankingService.cerrarTorneo(torneo.getId());
            campeonService.recalcularCampeones(torneoActual);
        }
    }

    public void actualizarRankingWO(Partido partido) {
        rankingService.actualizarRanking(partido);
    }

    public void avanzarBracketDespuesDeWO(Partido partido) {
        if (partido.getFase() != null && partido.getFase().equals(FasePartido.GRUPOS)) {
            actualizarPosicionesGrupoWO(partido);
            verificarAvanceEliminatorias(partido.getGrupo().getId(), partido.getTorneo());
        } else if (partido.getFase() != null && partido.getFase().equals(FasePartido.ELIMINACION)) {
            avanzarBracketSiCorresponde(partido);
        }
    }

    private void actualizarPosicionesGrupoWO(Partido partido) {
        PosicionGrupo posGanador = posicionGrupoRepository
                .findByGrupoIdAndParejaId(partido.getGrupo().getId(), partido.getGanador().getId())
                .orElse(null);
        if (posGanador == null) return;

        Pareja perdedor = partido.getGanador().getId().equals(partido.getLocal().getId())
                ? partido.getVisitante() : partido.getLocal();
        PosicionGrupo posPerdedor = perdedor != null
                ? posicionGrupoRepository.findByGrupoIdAndParejaId(partido.getGrupo().getId(), perdedor.getId()).orElse(null)
                : null;

        posGanador.setPj(posGanador.getPj() + 1);
        posGanador.setPg(posGanador.getPg() + 1);
        posGanador.setPuntos(posGanador.getPuntos() + 3);
        posicionGrupoRepository.save(posGanador);

        if (posPerdedor != null) {
            posPerdedor.setPj(posPerdedor.getPj() + 1);
            posPerdedor.setPp(posPerdedor.getPp() + 1);
            posicionGrupoRepository.save(posPerdedor);
        }
    }

    private String nombreRonda(int tamano) {
        return switch (tamano) {
            case 16 -> "Octavos de final";
            case 8 -> "Cuartos de final";
            case 4 -> "Semifinales";
            case 2 -> "Final";
            default -> "Eliminación";
        };
    }
}
