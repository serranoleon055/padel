package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.GastoRequest;
import com.padel.rankpadel.dto.response.GastoResponse;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.GastoRepository;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GastoService {

    private static final Logger log = LoggerFactory.getLogger(GastoService.class);

    private final GastoRepository gastoRepository;
    private final CajaCerradaGuard cajaCerradaGuard;
    private final DisponibilidadCanchaService disponibilidadCanchaService;

    /**
     * Un gasto es plata que ya salió: no puede tener fecha futura. Sin este control, un
     * error de tipeo en el año (2027 por 2026) se guardaba sin chistar y quedaba
     * escondido en un mes que todavía no llegó, ensuciando el resultado de ese mes.
     */
    private void exigirFechaNoFutura(LocalDate fecha) {
        if (fecha != null && fecha.isAfter(LocalDate.now())) {
            throw new EstadoInvalidoException("La fecha del gasto no puede ser futura.");
        }
    }

    @Transactional
    public GastoResponse registrar(GastoRequest request) {
        return aResponse(registrarEntidad(request, null, false));
    }

    /**
     * El alta de verdad. La usan tanto el endpoint de gastos como la compra de
     * mercadería: hasta acá la compra armaba el {@code Gasto} a mano y por eso se
     * salteaba el control de caja cerrada y el de fecha futura. Un solo camino, un solo
     * lugar donde viven las reglas.
     *
     * @param producto     el producto cuyo stock entró, si el gasto es una compra
     * @param esMercaderia si el egreso es inventario y no gasto operativo
     */
    @Transactional
    public Gasto registrarEntidad(GastoRequest request, Producto producto, boolean esMercaderia) {
        exigirFechaNoFutura(request.getFecha());
        // La plata sale del cajón HOY, en la jornada que el club está atendiendo, aunque
        // la fecha contable sea vieja: una factura de julio pagada hoy pesa en julio para
        // el resultado del mes, pero el efectivo falta en el arqueo de esta noche. Por eso
        // son dos campos distintos, y por eso el que bloquea es la jornada.
        LocalDate jornada = disponibilidadCanchaService.fechaDeJornadaActual();
        cajaCerradaGuard.exigirDiaAbierto(jornada);
        return gastoRepository.save(Gasto.builder()
                .fecha(request.getFecha())
                .jornada(jornada)
                .categoria(request.getCategoria())
                .descripcion(request.getDescripcion().trim())
                .monto(request.getMonto())
                .medio(request.getMedio())
                .proveedor(request.getProveedor() != null ? request.getProveedor().trim() : null)
                .registradoPor(UsuarioActual.nombre())
                .notas(request.getNotas())
                .creadoEn(LocalDateTime.now())
                .producto(producto)
                .esMercaderia(esMercaderia)
                .build());
    }

    @Transactional
    public GastoResponse actualizar(Long id, GastoRequest request) {
        Gasto gasto = buscar(id);
        exigirNoAnulado(gasto);
        exigirFechaNoFutura(request.getFecha());
        // Lo que bloquea es la jornada en la que entró la plata, no la fecha contable:
        // corregir a qué mes pesa un gasto no mueve ningún arqueo, pero cambiarle el
        // monto o el medio de pago sí, y los dos pasan por este mismo método.
        cajaCerradaGuard.exigirDiaAbierto(gasto.getJornada());
        gasto.setFecha(request.getFecha());
        gasto.setCategoria(request.getCategoria());
        gasto.setDescripcion(request.getDescripcion().trim());
        gasto.setMonto(request.getMonto());
        gasto.setMedio(request.getMedio());
        gasto.setProveedor(request.getProveedor() != null ? request.getProveedor().trim() : null);
        gasto.setNotas(request.getNotas());
        gastoRepository.save(gasto);
        return aResponse(gasto);
    }

    /**
     * Anular un egreso cambia la rentabilidad del mes y el efectivo esperado de una
     * jornada. Es baja lógica: la fila sale de todos los totales pero queda, igual que
     * los cobros y las ventas desde V51. Un gasto borrado no deja nada que auditar.
     */
    @Transactional
    public void anular(Long id, String motivo) {
        Gasto gasto = buscar(id);
        exigirNoAnulado(gasto);
        cajaCerradaGuard.exigirDiaAbierto(gasto.getJornada());
        gasto.setAnuladoEn(LocalDateTime.now());
        gasto.setAnuladoPor(UsuarioActual.nombre());
        gasto.setMotivoAnulacion(motivo);
        gastoRepository.save(gasto);
        log.info("[caja] {} anuló el gasto {} de ${} ({} - {}). Motivo: {}",
                gasto.getAnuladoPor(), id, gasto.getMonto(), gasto.getCategoria(),
                gasto.getDescripcion(), motivo);
    }

    /** Los egresos de una jornada, para el detalle del arqueo. */
    @Transactional(readOnly = true)
    public List<GastoResponse> listarDeLaJornada(LocalDate jornada) {
        return gastoRepository.findDeLaJornada(jornada).stream().map(this::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GastoResponse> listarAnuladosDeLaJornada(LocalDate jornada) {
        return gastoRepository.findAnuladosDeLaJornada(jornada).stream().map(this::aResponse).toList();
    }

    /** Por fecha contable: es el criterio del estado de resultados, no el de la caja. */
    @Transactional(readOnly = true)
    public List<GastoResponse> listarEntre(LocalDate desde, LocalDate hasta) {
        return gastoRepository.findEntreFechas(desde, hasta).stream()
                .map(this::aResponse)
                .toList();
    }

    private void exigirNoAnulado(Gasto gasto) {
        if (gasto.estaAnulado()) {
            throw new EstadoInvalidoException("Ese gasto ya está anulado.");
        }
    }

    private Gasto buscar(Long id) {
        return gastoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto", id));
    }

    private GastoResponse aResponse(Gasto gasto) {
        return GastoResponse.builder()
                .id(gasto.getId())
                .fecha(gasto.getFecha())
                .jornada(gasto.getJornada())
                .categoria(gasto.getCategoria() != null ? gasto.getCategoria().name() : null)
                .descripcion(gasto.getDescripcion())
                .monto(gasto.getMonto())
                .medio(gasto.getMedio() != null ? gasto.getMedio().name() : null)
                .proveedor(gasto.getProveedor())
                .registradoPor(gasto.getRegistradoPor())
                .notas(gasto.getNotas())
                .esMercaderia(gasto.isEsMercaderia())
                .anuladoEn(gasto.getAnuladoEn())
                .anuladoPor(gasto.getAnuladoPor())
                .motivoAnulacion(gasto.getMotivoAnulacion())
                .build();
    }
}
