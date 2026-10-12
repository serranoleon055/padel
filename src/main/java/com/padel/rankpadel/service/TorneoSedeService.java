package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.FranjaSedeDto;
import com.padel.rankpadel.dto.request.PagoSedeRequest;
import com.padel.rankpadel.dto.request.TorneoSedeRequest;
import com.padel.rankpadel.dto.response.TorneoSedeResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.FranjaSede;
import com.padel.rankpadel.entity.Lugar;
import com.padel.rankpadel.entity.PagoSede;
import com.padel.rankpadel.entity.Partido;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.entity.TorneoSede;
import com.padel.rankpadel.enums.EstadoPartido;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.LugarRepository;
import com.padel.rankpadel.repository.PartidoRepository;
import com.padel.rankpadel.repository.TorneoRepository;
import com.padel.rankpadel.repository.TorneoSedeRepository;
import com.padel.rankpadel.util.OrdenCanchas;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Los clubes donde se juega un torneo: qué canchas cede cada uno, cuándo, y cuánto se le
 * paga.
 *
 * <p>Lo que se le debe a cada club es un monto fijo por el torneo (no por hora ni por
 * partido): es como lo arregla el organizador. Se le puede pagar en partes, y el saldo es
 * siempre el acordado menos los pagos no anulados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TorneoSedeService {

    private final TorneoSedeRepository torneoSedeRepository;
    private final TorneoRepository torneoRepository;
    private final LugarRepository lugarRepository;
    private final CanchaRepository canchaRepository;
    private final PartidoRepository partidoRepository;

    @Transactional(readOnly = true)
    public List<TorneoSedeResponse> listar(Long torneoId) {
        Torneo torneo = buscarTorneo(torneoId);
        return torneoSedeRepository.findByTorneoIdOrderByIdAsc(torneoId).stream()
                .map(sede -> aResponse(sede, torneo))
                .toList();
    }

    @Transactional
    public TorneoSedeResponse agregar(Long torneoId, TorneoSedeRequest request) {
        Torneo torneo = buscarTorneo(torneoId);
        Lugar lugar = buscarLugar(request.getLugarId());
        if (lugar.isArchivado()) {
            throw new EstadoInvalidoException(lugar.getNombre() + " está archivado: reactivalo para usarlo.");
        }
        if (torneoSedeRepository.existsByTorneoIdAndLugarId(torneoId, lugar.getId())) {
            throw new EstadoInvalidoException(lugar.getNombre() + " ya es uno de los clubes del torneo.");
        }

        TorneoSede sede = TorneoSede.builder().torneo(torneo).lugar(lugar).build();
        aplicar(sede, request);
        torneoSedeRepository.save(sede);
        log.info("[sedes] {} sumó {} al torneo {}", UsuarioActual.nombre(), lugar.getNombre(), torneo.getNombre());
        return aResponse(sede, torneo);
    }

    @Transactional
    public TorneoSedeResponse actualizar(Long torneoId, Long sedeId, TorneoSedeRequest request) {
        TorneoSede sede = buscarSede(torneoId, sedeId);
        if (!sede.getLugar().getId().equals(request.getLugarId())) {
            throw new EstadoInvalidoException("No se puede cambiar el club de una sede: quitala y agregá el otro.");
        }
        List<Long> canchasAntes = sede.getCanchas().stream().map(Cancha::getId).toList();
        aplicar(sede, request);
        List<Long> quitadas = canchasAntes.stream()
                .filter(id -> sede.getCanchas().stream().noneMatch(c -> c.getId().equals(id)))
                .toList();
        exigirSinPartidosPendientesEn(torneoId, quitadas, "Hay partidos sin jugar programados en una de las canchas que querés quitar. Reprogramalos primero.");
        torneoSedeRepository.save(sede);
        return aResponse(sede, sede.getTorneo());
    }

    @Transactional
    public void quitar(Long torneoId, Long sedeId) {
        TorneoSede sede = buscarSede(torneoId, sedeId);
        Torneo torneo = sede.getTorneo();
        if (torneo.getLugar() != null && torneo.getLugar().getId().equals(sede.getLugar().getId())) {
            throw new EstadoInvalidoException(sede.getLugar().getNombre()
                    + " es el club principal del torneo: cambialo en la configuración antes de quitarlo.");
        }
        if (sede.getPagos().stream().anyMatch(pago -> !pago.estaAnulado())) {
            throw new EstadoInvalidoException("Ya le pagaste a " + sede.getLugar().getNombre()
                    + ": anulá los pagos antes de quitarlo del torneo.");
        }
        List<Long> canchas = canchaRepository.findByLugarId(sede.getLugar().getId()).stream().map(Cancha::getId).toList();
        exigirSinPartidosPendientesEn(torneoId, canchas, "Hay partidos sin jugar programados en " + sede.getLugar().getNombre()
                + ". Reprogramalos primero.");
        torneoSedeRepository.delete(sede);
    }

    @Transactional
    public TorneoSedeResponse registrarPago(Long torneoId, Long sedeId, PagoSedeRequest request) {
        TorneoSede sede = buscarSede(torneoId, sedeId);
        if (sede.getMontoAcordado() == null) {
            throw new EstadoInvalidoException("Primero cargá cuánto se acordó pagarle a " + sede.getLugar().getNombre() + ".");
        }
        if (request.getFecha().isAfter(LocalDate.now())) {
            throw new EstadoInvalidoException("La fecha del pago no puede ser futura.");
        }
        BigDecimal saldo = saldo(sede);
        if (request.getMonto().compareTo(saldo) > 0) {
            throw new EstadoInvalidoException("A " + sede.getLugar().getNombre() + " se le deben $" + saldo.toPlainString()
                    + ", no se puede pagar más que eso.");
        }
        sede.getPagos().add(PagoSede.builder()
                .sede(sede)
                .fecha(request.getFecha())
                .monto(request.getMonto())
                .medio(request.getMedio())
                .notas(request.getNotas())
                .registradoPor(UsuarioActual.nombre())
                .creadoEn(LocalDateTime.now())
                .build());
        torneoSedeRepository.save(sede);
        log.info("[sedes] {} le pagó ${} a {} por el torneo {}", UsuarioActual.nombre(), request.getMonto(),
                sede.getLugar().getNombre(), sede.getTorneo().getNombre());
        return aResponse(sede, sede.getTorneo());
    }

    @Transactional
    public TorneoSedeResponse anularPago(Long torneoId, Long sedeId, Long pagoId, String motivo) {
        TorneoSede sede = buscarSede(torneoId, sedeId);
        PagoSede pago = sede.getPagos().stream().filter(p -> p.getId().equals(pagoId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Pago al club", pagoId));
        if (pago.estaAnulado()) {
            throw new EstadoInvalidoException("Ese pago ya está anulado.");
        }
        pago.setAnuladoEn(LocalDateTime.now());
        pago.setAnuladoPor(UsuarioActual.nombre());
        pago.setMotivoAnulacion(motivo);
        torneoSedeRepository.save(sede);
        return aResponse(sede, sede.getTorneo());
    }

    /**
     * Que el club principal del torneo esté entre sus sedes. Lo llama el alta y la edición
     * del torneo: así un torneo nuevo ya arranca con su club y todas sus canchas.
     */
    @Transactional
    public void asegurarSedePrincipal(Torneo torneo) {
        if (torneo.getId() == null || torneo.getLugar() == null) {
            return;
        }
        Long lugarId = torneo.getLugar().getId();
        if (torneoSedeRepository.existsByTorneoIdAndLugarId(torneo.getId(), lugarId)) {
            return;
        }
        torneoSedeRepository.save(TorneoSede.builder()
                .torneo(torneo)
                .lugar(torneo.getLugar())
                .canchas(new ArrayList<>(canchaRepository.findByLugarIdAndActivoTrue(lugarId)))
                .build());
    }

    private void aplicar(TorneoSede sede, TorneoSedeRequest request) {
        Long lugarId = sede.getLugar().getId();
        List<Cancha> canchas;
        if (request.getCanchaIds() == null || request.getCanchaIds().isEmpty()) {
            canchas = canchaRepository.findByLugarIdAndActivoTrue(lugarId);
            if (canchas.isEmpty()) {
                throw new EstadoInvalidoException(sede.getLugar().getNombre()
                        + " no tiene canchas cargadas. Cargalas en Clubes y canchas.");
            }
        } else {
            canchas = canchaRepository.findAllById(new HashSet<>(request.getCanchaIds()));
            if (canchas.size() != new HashSet<>(request.getCanchaIds()).size()
                    || canchas.stream().anyMatch(c -> c.getLugar() == null || !lugarId.equals(c.getLugar().getId()))) {
                throw new EstadoInvalidoException("Las canchas elegidas tienen que ser de " + sede.getLugar().getNombre() + ".");
            }
        }
        sede.getCanchas().clear();
        sede.getCanchas().addAll(canchas);

        List<FranjaSede> franjas = validarFranjas(request.getFranjas());
        sede.getFranjas().clear();
        sede.getFranjas().addAll(franjas);

        BigDecimal pagado = totalPagado(sede);
        if (request.getMontoAcordado() != null && request.getMontoAcordado().compareTo(pagado) < 0) {
            throw new EstadoInvalidoException("Ya se le pagaron $" + pagado.toPlainString()
                    + " a este club: el monto acordado no puede ser menor.");
        }
        if (request.getMontoAcordado() == null && pagado.signum() > 0) {
            throw new EstadoInvalidoException("Este club ya tiene pagos: el monto acordado no se puede dejar vacío.");
        }
        sede.setMontoAcordado(request.getMontoAcordado());
        sede.setNotas(request.getNotas() != null && !request.getNotas().isBlank() ? request.getNotas().trim() : null);
    }

    static List<FranjaSede> validarFranjas(List<FranjaSedeDto> entrada) {
        if (entrada == null) {
            return List.of();
        }
        List<FranjaSede> franjas = entrada.stream()
                .map(f -> FranjaSede.builder().fecha(f.getFecha()).horaDesde(f.getHoraDesde()).horaHasta(f.getHoraHasta()).build())
                .sorted(Comparator.comparing(FranjaSede::getFecha).thenComparing(FranjaSede::getHoraDesde))
                .toList();
        for (int i = 0; i < franjas.size(); i++) {
            FranjaSede franja = franjas.get(i);
            if (!franja.getHoraHasta().isAfter(franja.getHoraDesde())) {
                throw new EstadoInvalidoException("La franja del " + franja.getFecha() + " termina antes de empezar ("
                        + franja.getHoraDesde() + " a " + franja.getHoraHasta() + ").");
            }
            if (i > 0) {
                FranjaSede anterior = franjas.get(i - 1);
                if (anterior.getFecha().equals(franja.getFecha()) && franja.getHoraDesde().isBefore(anterior.getHoraHasta())) {
                    throw new EstadoInvalidoException("Las franjas del " + franja.getFecha() + " se pisan: unilas en una sola.");
                }
            }
        }
        return new ArrayList<>(franjas);
    }

    private void exigirSinPartidosPendientesEn(Long torneoId, List<Long> canchaIds, String mensaje) {
        if (canchaIds.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>(canchaIds);
        boolean hay = partidoRepository.findByTorneoId(torneoId).stream()
                .filter(p -> p.getCancha() != null && ids.contains(p.getCancha().getId()))
                .map(Partido::getEstado)
                .anyMatch(estado -> estado == EstadoPartido.PENDIENTE || estado == EstadoPartido.EN_CURSO);
        if (hay) {
            throw new EstadoInvalidoException(mensaje);
        }
    }

    private TorneoSedeResponse aResponse(TorneoSede sede, Torneo torneo) {
        Lugar lugar = sede.getLugar();
        TorneoSedeResponse.TorneoSedeResponseBuilder respuesta = TorneoSedeResponse.builder()
                .id(sede.getId())
                .lugarId(lugar.getId())
                .lugarNombre(lugar.getNombre())
                .lugarDireccion(lugar.getDireccion())
                .principal(torneo.getLugar() != null && torneo.getLugar().getId().equals(lugar.getId()))
                .canchas(sede.getCanchas().stream()
                        .sorted(OrdenCanchas.porNombre(Cancha::getNombre))
                        .map(c -> TorneoSedeResponse.CanchaSede.builder().id(c.getId()).nombre(c.getNombre()).build())
                        .toList())
                .franjas(sede.getFranjas().stream()
                        .map(f -> FranjaSedeDto.builder().fecha(f.getFecha()).horaDesde(f.getHoraDesde()).horaHasta(f.getHoraHasta()).build())
                        .toList())
                .notas(sede.getNotas());

        if (UsuarioActual.esDuenio()) {
            BigDecimal pagado = totalPagado(sede);
            respuesta.montoAcordado(sede.getMontoAcordado())
                    .totalPagado(pagado)
                    .saldo(sede.getMontoAcordado() != null ? sede.getMontoAcordado().subtract(pagado) : null)
                    .pagos(sede.getPagos().stream().map(TorneoSedeService::aPago).toList());
        }
        return respuesta.build();
    }

    private static TorneoSedeResponse.Pago aPago(PagoSede pago) {
        return TorneoSedeResponse.Pago.builder()
                .id(pago.getId())
                .fecha(pago.getFecha())
                .monto(pago.getMonto())
                .medio(pago.getMedio())
                .notas(pago.getNotas())
                .registradoPor(pago.getRegistradoPor())
                .creadoEn(pago.getCreadoEn())
                .anulado(pago.estaAnulado())
                .anuladoPor(pago.getAnuladoPor())
                .motivoAnulacion(pago.getMotivoAnulacion())
                .build();
    }

    static BigDecimal totalPagado(TorneoSede sede) {
        return sede.getPagos().stream()
                .filter(pago -> !pago.estaAnulado())
                .map(PagoSede::getMonto)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal saldo(TorneoSede sede) {
        return sede.getMontoAcordado().subtract(totalPagado(sede));
    }

    private Torneo buscarTorneo(Long torneoId) {
        return torneoRepository.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", torneoId));
    }

    private Lugar buscarLugar(Long lugarId) {
        return lugarRepository.findById(lugarId)
                .orElseThrow(() -> new ResourceNotFoundException("Club", lugarId));
    }

    private TorneoSede buscarSede(Long torneoId, Long sedeId) {
        TorneoSede sede = torneoSedeRepository.findById(sedeId)
                .orElseThrow(() -> new ResourceNotFoundException("Club del torneo", sedeId));
        if (!sede.getTorneo().getId().equals(torneoId)) {
            throw new ResourceNotFoundException("Club del torneo", sedeId);
        }
        return sede;
    }
}
