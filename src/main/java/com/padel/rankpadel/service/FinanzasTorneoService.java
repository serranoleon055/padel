package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.AporteSponsorRequest;
import com.padel.rankpadel.dto.request.EntregaPremioRequest;
import com.padel.rankpadel.dto.request.GastoTorneoRequest;
import com.padel.rankpadel.dto.request.PremioTorneoRequest;
import com.padel.rankpadel.dto.response.CuentaInscripcionesResponse;
import com.padel.rankpadel.dto.response.FinanzasTorneoResponse;
import com.padel.rankpadel.entity.AporteSponsor;
import com.padel.rankpadel.entity.CampeonTorneo;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.entity.GastoTorneo;
import com.padel.rankpadel.entity.PagoSede;
import com.padel.rankpadel.entity.Pareja;
import com.padel.rankpadel.entity.PremioTorneo;
import com.padel.rankpadel.entity.Sponsor;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.entity.TorneoSede;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.AporteSponsorRepository;
import com.padel.rankpadel.repository.CampeonTorneoRepository;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.GastoTorneoRepository;
import com.padel.rankpadel.repository.PremioTorneoRepository;
import com.padel.rankpadel.repository.SponsorRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.repository.TorneoSedeRepository;
import com.padel.rankpadel.util.NormalizadorTexto;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * La plata de un torneo. Junta lo que ya existe (inscripciones cobradas, lo acordado y
 * pagado a cada club) con premios, gastos y aportes de sponsors, y arma el balance:
 * cuánto se va a ganar si todo se cobra y se paga, y cuánto se ganó hasta hoy.
 */
@Service
@RequiredArgsConstructor
public class FinanzasTorneoService {

    private final TorneoRepository torneoRepository;
    private final TorneoSedeRepository torneoSedeRepository;
    private final PremioTorneoRepository premioTorneoRepository;
    private final GastoTorneoRepository gastoTorneoRepository;
    private final AporteSponsorRepository aporteSponsorRepository;
    private final CategoriaRepository categoriaRepository;
    private final SponsorRepository sponsorRepository;
    private final CampeonTorneoRepository campeonTorneoRepository;
    private final CobroInscripcionService cobroInscripcionService;

    @Transactional(readOnly = true)
    public FinanzasTorneoResponse finanzas(Long torneoId) {
        buscarTorneo(torneoId);
        List<CampeonTorneo> campeones = campeonTorneoRepository.findByTorneoId(torneoId);
        return FinanzasTorneoResponse.builder()
                .balance(balance(torneoId))
                .premios(premioTorneoRepository.findByTorneoIdOrderByIdAsc(torneoId).stream()
                        .map(p -> aPremio(p, campeones)).toList())
                .gastos(gastoTorneoRepository.findByTorneoIdOrderByFechaAscIdAsc(torneoId).stream()
                        .map(FinanzasTorneoService::aGasto).toList())
                .aportes(aporteSponsorRepository.findByTorneoIdOrderByIdAsc(torneoId).stream()
                        .map(FinanzasTorneoService::aAporte).toList())
                .build();
    }

    /** El balance solo, para el panel del organizador. */
    @Transactional(readOnly = true)
    public FinanzasTorneoResponse.Balance balance(Long torneoId) {
        CuentaInscripcionesResponse inscripciones = cobroInscripcionService.cuenta(torneoId);

        List<AporteSponsor> aportes = aporteSponsorRepository.findByTorneoIdOrderByIdAsc(torneoId);
        BigDecimal sponsorsComprometido = sumar(aportes.stream().map(AporteSponsor::getMonto).toList());
        BigDecimal sponsorsCobrado = sumar(aportes.stream().filter(AporteSponsor::isCobrado).map(AporteSponsor::getMonto).toList());

        List<TorneoSede> sedes = torneoSedeRepository.findByTorneoIdOrderByIdAsc(torneoId);
        BigDecimal clubesAcordado = sumar(sedes.stream().map(TorneoSede::getMontoAcordado).toList());
        BigDecimal clubesPagado = sumar(sedes.stream().flatMap(s -> s.getPagos().stream())
                .filter(p -> !p.estaAnulado()).map(PagoSede::getMonto).toList());

        List<PremioTorneo> premios = premioTorneoRepository.findByTorneoIdOrderByIdAsc(torneoId);
        BigDecimal premiosTotal = sumar(premios.stream().map(PremioTorneo::getMonto).toList());
        BigDecimal premiosEntregado = sumar(premios.stream().filter(PremioTorneo::isEntregado).map(PremioTorneo::getMonto).toList());

        BigDecimal gastos = sumar(gastoTorneoRepository.findByTorneoIdOrderByFechaAscIdAsc(torneoId).stream()
                .filter(g -> !g.estaAnulado()).map(GastoTorneo::getMonto).toList());

        BigDecimal ingresosProyectados = inscripciones.getTotalEsperado().add(sponsorsComprometido);
        BigDecimal ingresosReales = inscripciones.getTotalCobrado().add(sponsorsCobrado);
        BigDecimal egresosProyectados = clubesAcordado.add(premiosTotal).add(gastos);
        BigDecimal egresosReales = clubesPagado.add(premiosEntregado).add(gastos);

        return FinanzasTorneoResponse.Balance.builder()
                .inscripcionesEsperado(inscripciones.getTotalEsperado())
                .inscripcionesCobrado(inscripciones.getTotalCobrado())
                .sponsorsComprometido(sponsorsComprometido)
                .sponsorsCobrado(sponsorsCobrado)
                .clubesAcordado(clubesAcordado)
                .clubesPagado(clubesPagado)
                .premiosTotal(premiosTotal)
                .premiosEntregado(premiosEntregado)
                .gastos(gastos)
                .resultadoProyectado(ingresosProyectados.subtract(egresosProyectados))
                .resultadoReal(ingresosReales.subtract(egresosReales))
                .porCobrar(ingresosProyectados.subtract(ingresosReales))
                .porPagar(clubesAcordado.subtract(clubesPagado).add(premiosTotal.subtract(premiosEntregado)))
                .build();
    }

    // ── Premios ─────────────────────────────────────────────────────────────

    @Transactional
    public FinanzasTorneoResponse agregarPremio(Long torneoId, PremioTorneoRequest request) {
        PremioTorneo premio = PremioTorneo.builder().torneo(buscarTorneo(torneoId)).build();
        aplicar(premio, request);
        premioTorneoRepository.save(premio);
        return finanzas(torneoId);
    }

    @Transactional
    public FinanzasTorneoResponse editarPremio(Long torneoId, Long premioId, PremioTorneoRequest request) {
        PremioTorneo premio = buscarPremio(torneoId, premioId);
        aplicar(premio, request);
        premioTorneoRepository.save(premio);
        return finanzas(torneoId);
    }

    @Transactional
    public FinanzasTorneoResponse entregarPremio(Long torneoId, Long premioId, EntregaPremioRequest request) {
        PremioTorneo premio = buscarPremio(torneoId, premioId);
        premio.setEntregado(request.isEntregado());
        premio.setEntregadoEn(request.isEntregado() ? LocalDateTime.now() : null);
        premio.setEntregadoA(request.isEntregado() ? limpiar(request.getEntregadoA()) : null);
        premioTorneoRepository.save(premio);
        return finanzas(torneoId);
    }

    @Transactional
    public FinanzasTorneoResponse quitarPremio(Long torneoId, Long premioId) {
        PremioTorneo premio = buscarPremio(torneoId, premioId);
        if (premio.isEntregado()) {
            throw new EstadoInvalidoException("Ese premio ya se entregó: desmarcalo antes de borrarlo.");
        }
        premioTorneoRepository.delete(premio);
        return finanzas(torneoId);
    }

    private void aplicar(PremioTorneo premio, PremioTorneoRequest request) {
        if (request.getMonto() == null && limpiar(request.getDescripcion()) == null) {
            throw new EstadoInvalidoException("Indicá el monto del premio o qué se entrega.");
        }
        Categoria categoria = null;
        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría", request.getCategoriaId()));
            Long categoriaId = categoria.getId();
            if (premio.getTorneo().getCategorias().stream().noneMatch(c -> c.getId().equals(categoriaId))) {
                throw new EstadoInvalidoException(categoria.getNombre() + " no es una categoría de este torneo.");
            }
        }
        premio.setCategoria(categoria);
        premio.setPuesto(request.getPuesto().trim());
        premio.setDescripcion(limpiar(request.getDescripcion()));
        premio.setMonto(request.getMonto());
    }

    // ── Gastos ──────────────────────────────────────────────────────────────

    @Transactional
    public FinanzasTorneoResponse registrarGasto(Long torneoId, GastoTorneoRequest request) {
        gastoTorneoRepository.save(GastoTorneo.builder()
                .torneo(buscarTorneo(torneoId))
                .fecha(request.getFecha())
                .categoria(request.getCategoria())
                .descripcion(limpiar(request.getDescripcion()))
                .monto(request.getMonto())
                .medio(request.getMedio())
                .registradoPor(UsuarioActual.nombre())
                .creadoEn(LocalDateTime.now())
                .build());
        return finanzas(torneoId);
    }

    @Transactional
    public FinanzasTorneoResponse anularGasto(Long torneoId, Long gastoId, String motivo) {
        GastoTorneo gasto = gastoTorneoRepository.findById(gastoId)
                .filter(g -> g.getTorneo().getId().equals(torneoId))
                .orElseThrow(() -> new ResourceNotFoundException("Gasto", gastoId));
        if (gasto.estaAnulado()) {
            throw new EstadoInvalidoException("Ese gasto ya está anulado.");
        }
        gasto.setAnuladoEn(LocalDateTime.now());
        gasto.setAnuladoPor(UsuarioActual.nombre());
        gasto.setMotivoAnulacion(motivo);
        gastoTorneoRepository.save(gasto);
        return finanzas(torneoId);
    }

    // ── Sponsors ────────────────────────────────────────────────────────────

    @Transactional
    public FinanzasTorneoResponse agregarAporte(Long torneoId, AporteSponsorRequest request) {
        AporteSponsor aporte = AporteSponsor.builder().torneo(buscarTorneo(torneoId)).build();
        aplicar(aporte, request);
        aporteSponsorRepository.save(aporte);
        return finanzas(torneoId);
    }

    @Transactional
    public FinanzasTorneoResponse editarAporte(Long torneoId, Long aporteId, AporteSponsorRequest request) {
        AporteSponsor aporte = buscarAporte(torneoId, aporteId);
        aplicar(aporte, request);
        aporteSponsorRepository.save(aporte);
        return finanzas(torneoId);
    }

    @Transactional
    public FinanzasTorneoResponse quitarAporte(Long torneoId, Long aporteId) {
        AporteSponsor aporte = buscarAporte(torneoId, aporteId);
        if (aporte.isCobrado()) {
            throw new EstadoInvalidoException("Ese aporte ya se cobró: desmarcalo antes de borrarlo.");
        }
        aporteSponsorRepository.delete(aporte);
        return finanzas(torneoId);
    }

    private void aplicar(AporteSponsor aporte, AporteSponsorRequest request) {
        Sponsor sponsor = null;
        if (request.getSponsorId() != null) {
            sponsor = sponsorRepository.findById(request.getSponsorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sponsor", request.getSponsorId()));
        }
        String nombre = sponsor != null ? sponsor.getNombre() : limpiar(request.getNombre());
        if (nombre == null) {
            throw new EstadoInvalidoException("Indicá quién es el sponsor.");
        }
        if (request.getMonto() == null && limpiar(request.getDescripcion()) == null) {
            throw new EstadoInvalidoException("Indicá cuánta plata pone o qué aporta en productos.");
        }
        if (request.isCobrado() && request.getMonto() != null && request.getMedio() == null) {
            throw new EstadoInvalidoException("Indicá cómo se cobró.");
        }
        aporte.setSponsor(sponsor);
        aporte.setNombre(nombre);
        aporte.setMonto(request.getMonto());
        aporte.setDescripcion(limpiar(request.getDescripcion()));
        aporte.setCobrado(request.isCobrado());
        aporte.setCobradoEn(request.isCobrado() ? Objects.requireNonNullElse(request.getCobradoEn(), LocalDate.now()) : null);
        aporte.setMedio(request.isCobrado() ? request.getMedio() : null);
    }

    // ── Conversiones y ayudas ───────────────────────────────────────────────

    private static FinanzasTorneoResponse.Premio aPremio(PremioTorneo premio, List<CampeonTorneo> campeones) {
        return FinanzasTorneoResponse.Premio.builder()
                .id(premio.getId())
                .categoriaId(premio.getCategoria() != null ? premio.getCategoria().getId() : null)
                .categoriaNombre(premio.getCategoria() != null ? premio.getCategoria().getNombre() : null)
                .puesto(premio.getPuesto())
                .descripcion(premio.getDescripcion())
                .monto(premio.getMonto())
                .entregado(premio.isEntregado())
                .entregadoEn(premio.getEntregadoEn())
                .entregadoA(premio.getEntregadoA())
                .ganador(ganador(premio, campeones))
                .build();
    }

    /** La pareja que salió campeona o subcampeona de la categoría del premio, si el puesto es ese. */
    static String ganador(PremioTorneo premio, List<CampeonTorneo> campeones) {
        if (premio.getCategoria() == null) {
            return null;
        }
        String puesto = NormalizadorTexto.normalizar(premio.getPuesto());
        boolean subcampeon = puesto.contains("subcampeon") || puesto.contains("finalista") && !puesto.contains("semi");
        boolean campeon = !subcampeon && puesto.contains("campeon");
        if (!campeon && !subcampeon) {
            return null;
        }
        return campeones.stream()
                .filter(c -> c.getCategoria() != null && c.getCategoria().getId().equals(premio.getCategoria().getId()))
                .map(c -> campeon ? c.getParejaCampeona() : c.getParejaSubcampeona())
                .filter(Objects::nonNull)
                .map(FinanzasTorneoService::nombrePareja)
                .findFirst()
                .orElse(null);
    }

    private static String nombrePareja(Pareja pareja) {
        return nombre(pareja.getJugador1()) + " / " + nombre(pareja.getJugador2());
    }

    private static String nombre(com.padel.rankpadel.entity.Jugador jugador) {
        return jugador == null ? "?" : (jugador.getNombre() + " " + jugador.getApellido()).trim();
    }

    private static FinanzasTorneoResponse.Gasto aGasto(GastoTorneo gasto) {
        return FinanzasTorneoResponse.Gasto.builder()
                .id(gasto.getId())
                .fecha(gasto.getFecha())
                .categoria(gasto.getCategoria())
                .descripcion(gasto.getDescripcion())
                .monto(gasto.getMonto())
                .medio(gasto.getMedio())
                .registradoPor(gasto.getRegistradoPor())
                .anulado(gasto.estaAnulado())
                .anuladoPor(gasto.getAnuladoPor())
                .motivoAnulacion(gasto.getMotivoAnulacion())
                .build();
    }

    private static FinanzasTorneoResponse.Aporte aAporte(AporteSponsor aporte) {
        return FinanzasTorneoResponse.Aporte.builder()
                .id(aporte.getId())
                .sponsorId(aporte.getSponsor() != null ? aporte.getSponsor().getId() : null)
                .nombre(aporte.getNombre())
                .monto(aporte.getMonto())
                .descripcion(aporte.getDescripcion())
                .cobrado(aporte.isCobrado())
                .cobradoEn(aporte.getCobradoEn())
                .medio(aporte.getMedio())
                .build();
    }

    private static BigDecimal sumar(List<BigDecimal> montos) {
        return montos.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private Torneo buscarTorneo(Long torneoId) {
        return torneoRepository.findById(torneoId).orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));
    }

    private PremioTorneo buscarPremio(Long torneoId, Long premioId) {
        return premioTorneoRepository.findById(premioId)
                .filter(p -> p.getTorneo().getId().equals(torneoId))
                .orElseThrow(() -> new ResourceNotFoundException("Premio", premioId));
    }

    private AporteSponsor buscarAporte(Long torneoId, Long aporteId) {
        return aporteSponsorRepository.findById(aporteId)
                .filter(a -> a.getTorneo().getId().equals(torneoId))
                .orElseThrow(() -> new ResourceNotFoundException("Aporte", aporteId));
    }
}
