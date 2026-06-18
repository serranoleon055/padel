package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.LoteReservaRequest;
import com.padel.rankpadel.dto.request.PagoInscripcionRequest;
import com.padel.rankpadel.dto.response.PagoCreadoResponse;
import com.padel.rankpadel.dto.response.PagoResponse;
import com.padel.rankpadel.entity.Cancha;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.entity.Torneo;
import com.padel.rankpadel.enums.ConceptoPago;
import com.padel.rankpadel.enums.EstadoPago;
import com.padel.rankpadel.enums.EstadoReserva;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.CanchaRepository;
import com.padel.rankpadel.repository.PagoRepository;
import com.padel.rankpadel.repository.ReservaRepository;
import com.padel.rankpadel.repository.SolicitudInscripcionRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository pagoRepository;
    private final CanchaRepository canchaRepository;
    private final TorneoRepository torneoRepository;
    private final ReservaRepository reservaRepository;
    private final SolicitudInscripcionRepository solicitudInscripcionRepository;
    private final ReservaService reservaService;
    private final InscripcionService inscripcionService;
    private final DisponibilidadCanchaService disponibilidadCanchaService;
    private final MercadoPagoService mercadoPagoService;

    @Value("${app.pagos.porcentaje-senia-default:50}")
    private int porcentajeSeniaDefault;

    @Value("${app.pagos.expiracion-pago-minutos:30}")
    private int expiracionPagoMinutos;

    @Value("${app.mercadopago.back-url-base:http://localhost:5173}")
    private String backUrlBase;

    @Value("${app.mercadopago.notification-url:}")
    private String notificationUrl;

    @Value("${app.pagos.modo-demo:false}")
    private boolean modoDemo;

    @Transactional
    public PagoCreadoResponse crearPagoReserva(LoteReservaRequest request) {
        Cancha cancha = canchaRepository.findById(request.getCanchaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", request.getCanchaId()));
        if (cancha.getPrecioPorHora() == null) {
            throw new EstadoInvalidoException("Esta cancha no tiene precio configurado para pago online");
        }

        long cantidadSlots = request.getHorarios().stream().distinct().count();
        BigDecimal horasPorSlot = BigDecimal.valueOf(disponibilidadCanchaService.duracionSlot(cancha.getId()))
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal montoTotal = cancha.getPrecioPorHora()
                .multiply(horasPorSlot)
                .multiply(BigDecimal.valueOf(cantidadSlots))
                .setScale(2, RoundingMode.HALF_UP);

        int porcentaje = resolverPorcentajeSenia(cancha.getSeniaPorcentaje());
        BigDecimal montoSenia = calcularSenia(montoTotal, porcentaje);

        Pago pago = crearPagoPendiente(ConceptoPago.RESERVA, montoTotal, montoSenia, porcentaje,
                request.getClienteNombre(), request.getClienteTelefono());

        reservaService.crearReservasParaPago(request, pago, expiracionPagoMinutos);

        String urlResultado = backUrlBase + "/reservar/pago/resultado?pagoId=" + pago.getId();
        return iniciarPreferencia(pago, "Seña reserva de cancha " + cancha.getNombre(), urlResultado);
    }

    @Transactional
    public PagoCreadoResponse crearPagoInscripcion(PagoInscripcionRequest request) {
        Torneo torneo = torneoRepository.findById(request.getTorneoId())
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", request.getTorneoId()));
        if (torneo.getCostoInscripcionJugador() == null) {
            throw new EstadoInvalidoException("Este torneo no tiene costo de inscripción configurado para pago online");
        }

        BigDecimal montoTotal = torneo.getCostoInscripcionJugador()
                .multiply(BigDecimal.valueOf(2))
                .setScale(2, RoundingMode.HALF_UP);
        int porcentaje = resolverPorcentajeSenia(torneo.getSeniaPorcentaje());
        BigDecimal montoSenia = calcularSenia(montoTotal, porcentaje);

        Pago pago = crearPagoPendiente(ConceptoPago.INSCRIPCION, montoTotal, montoSenia, porcentaje,
                null, request.getInscripcion().getTelefonoContacto());

        inscripcionService.crearParaPago(request.getTorneoId(), request.getInscripcion(), pago);

        String urlResultado = backUrlBase + "/torneos/" + request.getTorneoId() + "/pago/resultado?pagoId=" + pago.getId();
        return iniciarPreferencia(pago, "Seña inscripción " + torneo.getNombre(), urlResultado);
    }

    @Transactional
    public PagoResponse obtenerPago(Long id) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));
        if (pago.getEstado() == EstadoPago.PENDIENTE) {
            mercadoPagoService.buscarPagoAprobado(pago.getReferenciaExterna())
                    .ifPresent(aprobado -> confirmarPagoAprobado(pago.getReferenciaExterna(), aprobado.id()));
        }
        return aResponse(pago);
    }

    @Transactional
    public void procesarNotificacion(Long pagoMercadoPagoId) {
        if (pagoMercadoPagoId == null) {
            return;
        }
        mercadoPagoService.obtenerPago(pagoMercadoPagoId).ifPresent(pagoMp -> {
            if ("approved".equals(pagoMp.estado()) && pagoMp.referenciaExterna() != null) {
                confirmarPagoAprobado(pagoMp.referenciaExterna(), pagoMp.id());
            }
        });
    }

    @Transactional(readOnly = true)
    public List<String> referenciasPendientesRecientes() {
        return pagoRepository.findByEstadoAndCreadoEnAfter(EstadoPago.PENDIENTE, LocalDateTime.now().minusMinutes(60))
                .stream()
                .map(Pago::getReferenciaExterna)
                .toList();
    }

    @Transactional
    public void sincronizarPago(String referenciaExterna) {
        mercadoPagoService.buscarPagoAprobado(referenciaExterna)
                .ifPresent(aprobado -> confirmarPagoAprobado(referenciaExterna, aprobado.id()));
    }

    @Transactional
    public void confirmarPagoAprobado(String referenciaExterna, String pagoMercadoPagoId) {
        Pago pago = pagoRepository.findByReferenciaExterna(referenciaExterna).orElse(null);
        if (pago == null || pago.getEstado() == EstadoPago.APROBADO) {
            return;
        }
        pago.setEstado(EstadoPago.APROBADO);
        pago.setPagadoEn(LocalDateTime.now());
        pago.setPagoMercadoPagoId(pagoMercadoPagoId);
        pagoRepository.save(pago);

        if (pago.getConcepto() == ConceptoPago.RESERVA) {
            for (Reserva reserva : reservaRepository.findByPagoId(pago.getId())) {
                if (reserva.getEstado() == EstadoReserva.PENDIENTE) {
                    reserva.setEstado(EstadoReserva.CONFIRMADA);
                    reserva.setConfirmadoEn(LocalDateTime.now());
                    reservaRepository.save(reserva);
                }
            }
        } else {
            SolicitudInscripcion solicitud = solicitudInscripcionRepository.findByPagoId(pago.getId());
            if (solicitud != null) {
                solicitud.setPagada(true);
                solicitudInscripcionRepository.save(solicitud);
            }
        }
    }

    private Pago crearPagoPendiente(ConceptoPago concepto, BigDecimal montoTotal, BigDecimal montoSenia,
            int porcentaje, String clienteNombre, String clienteTelefono) {
        Pago pago = Pago.builder()
                .concepto(concepto)
                .estado(EstadoPago.PENDIENTE)
                .montoTotal(montoTotal)
                .montoSenia(montoSenia)
                .porcentajeSenia(porcentaje)
                .referenciaExterna(UUID.randomUUID().toString())
                .clienteNombre(clienteNombre)
                .clienteTelefono(clienteTelefono)
                .creadoEn(LocalDateTime.now())
                .build();
        return pagoRepository.save(pago);
    }

    private PagoCreadoResponse iniciarPreferencia(Pago pago, String titulo, String urlResultado) {
        if (modoDemo) {
            confirmarPagoAprobado(pago.getReferenciaExterna(), "DEMO");
            return PagoCreadoResponse.builder()
                    .pagoId(pago.getId())
                    .initPoint(urlResultado)
                    .build();
        }
        MercadoPagoService.PreferenciaCreada preferencia = mercadoPagoService.crearPreferencia(
                pago.getReferenciaExterna(), titulo, pago.getMontoSenia(),
                urlResultado, urlResultado, urlResultado, notificationUrl);
        pago.setPreferenciaId(preferencia.id());
        pagoRepository.save(pago);
        return PagoCreadoResponse.builder()
                .pagoId(pago.getId())
                .initPoint(preferencia.initPoint())
                .build();
    }

    private int resolverPorcentajeSenia(Integer porcentajeConfigurado) {
        int porcentaje = porcentajeConfigurado != null ? porcentajeConfigurado : porcentajeSeniaDefault;
        if (porcentaje <= 0 || porcentaje > 100) {
            return porcentajeSeniaDefault;
        }
        return porcentaje;
    }

    private BigDecimal calcularSenia(BigDecimal montoTotal, int porcentaje) {
        return montoTotal.multiply(BigDecimal.valueOf(porcentaje))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private PagoResponse aResponse(Pago pago) {
        return PagoResponse.builder()
                .id(pago.getId())
                .concepto(pago.getConcepto() != null ? pago.getConcepto().name() : null)
                .estado(pago.getEstado() != null ? pago.getEstado().name() : null)
                .montoTotal(pago.getMontoTotal())
                .montoSenia(pago.getMontoSenia())
                .build();
    }
}
