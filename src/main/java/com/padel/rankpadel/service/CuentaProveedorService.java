package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.PagoProveedorRequest;
import com.padel.rankpadel.dto.response.CuentaProveedorResponse;
import com.padel.rankpadel.entity.DocumentoCompra;
import com.padel.rankpadel.entity.PagoProveedor;
import com.padel.rankpadel.entity.Proveedor;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.DocumentoCompraRepository;
import com.padel.rankpadel.repository.PagoProveedorRepository;
import com.padel.rankpadel.repository.ProveedorRepository;
import com.padel.rankpadel.util.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * Qué le debe el club a cada proveedor.
 *
 * <p>El saldo son las compras que quedaron a cuenta menos lo que se le pagó. Una compra
 * pagada en el momento no entra: esa plata ya salió por la caja y contarla acá sería
 * cobrarla dos veces.
 */
@Service
@RequiredArgsConstructor
public class CuentaProveedorService {

    private static final Logger log = LoggerFactory.getLogger(CuentaProveedorService.class);

    private final DocumentoCompraRepository documentoCompraRepository;
    private final PagoProveedorRepository pagoProveedorRepository;
    private final ProveedorRepository proveedorRepository;
    private final CajaCerradaGuard cajaCerradaGuard;
    private final DisponibilidadCanchaService disponibilidadCanchaService;

    @Transactional(readOnly = true)
    public CuentaProveedorResponse cuenta(Long proveedorId) {
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", proveedorId));

        List<DocumentoCompra> compras = documentoCompraRepository.findACreditoDe(proveedorId);
        List<PagoProveedor> pagos = pagoProveedorRepository.findDe(proveedorId);

        // Las dos cosas en una línea de tiempo, para que el club pueda seguir la cuenta
        // igual que la lee en el cuaderno: cada fila con el saldo que quedó después.
        List<CuentaProveedorResponse.Movimiento> movimientos = new ArrayList<>();
        for (DocumentoCompra compra : compras) {
            movimientos.add(CuentaProveedorResponse.Movimiento.builder()
                    .fecha(compra.getFecha())
                    .tipo("COMPRA")
                    .descripcion(compra.getTipoComprobante() + " " + compra.getNumero())
                    .monto(compra.getTotal())
                    .build());
        }
        for (PagoProveedor pago : pagos) {
            movimientos.add(CuentaProveedorResponse.Movimiento.builder()
                    .fecha(pago.getFecha())
                    .tipo("PAGO")
                    .descripcion("Pago " + (pago.getMedio() != null ? pago.getMedio().name().toLowerCase() : ""))
                    .monto(pago.getMonto().negate())
                    .build());
        }
        movimientos.sort(Comparator.comparing(CuentaProveedorResponse.Movimiento::getFecha));

        BigDecimal acumulado = BigDecimal.ZERO;
        for (CuentaProveedorResponse.Movimiento movimiento : movimientos) {
            acumulado = acumulado.add(movimiento.getMonto());
            movimiento.setSaldoAcumulado(acumulado);
        }

        BigDecimal comprasACredito = compras.stream()
                .map(DocumentoCompra::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagado = pagos.stream()
                .map(PagoProveedor::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);

        return CuentaProveedorResponse.builder()
                .proveedorId(proveedor.getId())
                .proveedorNombre(proveedor.getNombre())
                .comprasACredito(comprasACredito)
                .pagado(pagado)
                .saldo(comprasACredito.subtract(pagado))
                .movimientos(movimientos)
                .build();
    }

    /**
     * Registra un pago al proveedor. ESTO sale del cajón; la compra a crédito no.
     *
     * <p>No se puede pagar más de lo que se debe: es el mismo error de tipeo del mostrador
     * que ya se ataja al cobrar un turno.
     */
    @Transactional
    public CuentaProveedorResponse pagar(Long proveedorId, PagoProveedorRequest request) {
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", proveedorId));
        if (request.getFecha().isAfter(LocalDate.now())) {
            throw new EstadoInvalidoException("La fecha del pago no puede ser futura.");
        }

        BigDecimal saldo = documentoCompraRepository.totalACreditoDe(proveedorId)
                .subtract(pagoProveedorRepository.totalPagadoA(proveedorId));
        if (request.getMonto().compareTo(saldo) > 0) {
            throw new EstadoInvalidoException("A " + proveedor.getNombre() + " se le deben $"
                    + saldo + ", no se puede pagar más que eso.");
        }

        LocalDate jornada = disponibilidadCanchaService.fechaDeJornadaActual();
        cajaCerradaGuard.exigirDiaAbierto(jornada);

        pagoProveedorRepository.save(PagoProveedor.builder()
                .proveedor(proveedor)
                .fecha(request.getFecha())
                .jornada(jornada)
                .monto(request.getMonto())
                .medio(request.getMedio())
                .notas(request.getNotas())
                .registradoPor(UsuarioActual.nombre())
                .creadoEn(LocalDateTime.now())
                .build());

        log.info("[compras] {} le pagó ${} a {} por {}", UsuarioActual.nombre(),
                request.getMonto(), proveedor.getNombre(), request.getMedio());
        return cuenta(proveedorId);
    }

    /** Baja lógica, igual que todo lo demás que mueve plata. */
    @Transactional
    public void anularPago(Long pagoId, String motivo) {
        PagoProveedor pago = pagoProveedorRepository.findById(pagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago a proveedor", pagoId));
        if (pago.estaAnulado()) {
            throw new EstadoInvalidoException("Ese pago ya está anulado.");
        }
        cajaCerradaGuard.exigirDiaAbierto(pago.getJornada());
        pago.setAnuladoEn(LocalDateTime.now());
        pago.setAnuladoPor(UsuarioActual.nombre());
        pago.setMotivoAnulacion(motivo);
        pagoProveedorRepository.save(pago);
    }
}
