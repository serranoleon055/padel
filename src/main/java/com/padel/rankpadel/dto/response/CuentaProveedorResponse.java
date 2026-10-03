package com.padel.rankpadel.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Qué le debe el club a un proveedor y por qué.
 *
 * <p>El saldo son las compras que quedaron a cuenta menos lo que se le pagó. Una compra
 * pagada en el momento no entra: esa plata ya salió por la caja.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CuentaProveedorResponse {

    private Long proveedorId;
    private String proveedorNombre;

    /** Compras a cuenta corriente, acumuladas. */
    private BigDecimal comprasACredito;
    /** Lo que se le pagó. */
    private BigDecimal pagado;
    /**
     * Positivo = el club le debe. Negativo = hay saldo a favor del club, que pasa cuando
     * se anula una compra que ya estaba pagada: los pagos son a la cuenta del proveedor y
     * no a un comprobante, así que la plata queda ahí para descontar de la próxima. No es
     * un error: es justamente lo que hay que ver para ir a reclamarla.
     */
    private BigDecimal saldo;

    private List<Movimiento> movimientos;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Movimiento {
        private LocalDate fecha;
        /** COMPRA o PAGO. */
        private String tipo;
        private String descripcion;
        /** Positivo si sube la deuda (compra), negativo si la baja (pago). */
        private BigDecimal monto;
        private BigDecimal saldoAcumulado;
    }
}
