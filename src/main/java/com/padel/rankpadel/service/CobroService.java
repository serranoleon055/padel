package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.CobroRequest;
import com.padel.rankpadel.dto.response.CobroResponse;
import com.padel.rankpadel.entity.Cobro;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CobroRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.util.MontosReserva;

import lombok.RequiredArgsConstructor;

/**
 * Cobros en el mostrador. La seña online cubre una parte del turno; el resto entra acá
 * y es lo que alimenta el cierre de caja.
 */
@Service
@RequiredArgsConstructor
public class CobroService {

    private static final Logger log = LoggerFactory.getLogger(CobroService.class);

    /**
     * Estados sobre los que tiene sentido cobrar. Un turno cancelado o expirado no se
     * cobra; uno al que el cliente no vino sí, si el club decide cobrárselo igual.
     */
    private static final Set<EstadoReserva> COBRABLES = Set.of(
            EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA, EstadoReserva.NO_SHOW);

    private final CobroRepository cobroRepository;
    private final ReservaRepository reservaRepository;

    @Transactional
    public CobroResponse registrar(Long reservaId, CobroRequest request) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", reservaId));
        if (!COBRABLES.contains(reserva.getEstado())) {
            throw new EstadoInvalidoException("No se puede cobrar un turno en estado "
                    + reserva.getEstado());
        }

        BigDecimal saldo = MontosReserva.saldo(reserva, cobroRepository.totalCobradoDe(reservaId));
        if (saldo.compareTo(BigDecimal.ZERO) == 0) {
            throw new EstadoInvalidoException("Este turno ya está pago");
        }
        // Cobrar de más suele ser un error de tipeo en el mostrador, no una propina.
        if (request.getMonto().compareTo(saldo) > 0) {
            throw new EstadoInvalidoException("El monto supera el saldo pendiente ($" + saldo + ")");
        }

        Cobro cobro = cobroRepository.save(Cobro.builder()
                .reserva(reserva)
                .monto(request.getMonto())
                .medio(request.getMedio())
                .cobradoEn(LocalDateTime.now())
                .registradoPor(usuarioActual())
                .notas(request.getNotas())
                .build());

        return aResponse(cobro);
    }

    @Transactional(readOnly = true)
    public List<CobroResponse> listarDeReserva(Long reservaId) {
        return cobroRepository.findByReservaIdOrderByCobradoEnAsc(reservaId).stream()
                .map(this::aResponse)
                .toList();
    }

    /** Anula un cobro cargado por error. Queda en el log quién lo hizo y por cuánto. */
    @Transactional
    public void anular(Long id) {
        Cobro cobro = cobroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cobro", id));
        log.info("[caja] {} anuló el cobro {} de ${} ({}) de la reserva {}",
                usuarioActual(), id, cobro.getMonto(), cobro.getMedio(),
                cobro.getReserva() != null ? cobro.getReserva().getId() : null);
        cobroRepository.delete(cobro);
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    CobroResponse aResponse(Cobro cobro) {
        Reserva reserva = cobro.getReserva();
        return CobroResponse.builder()
                .id(cobro.getId())
                .reservaId(reserva != null ? reserva.getId() : null)
                .monto(cobro.getMonto())
                .medio(cobro.getMedio() != null ? cobro.getMedio().name() : null)
                .cobradoEn(cobro.getCobradoEn())
                .registradoPor(cobro.getRegistradoPor())
                .notas(cobro.getNotas())
                .clienteNombre(reserva != null ? reserva.getClienteNombre() : null)
                .canchaNombre(reserva != null && reserva.getCancha() != null
                        ? reserva.getCancha().getNombre() : null)
                .build();
    }
}
