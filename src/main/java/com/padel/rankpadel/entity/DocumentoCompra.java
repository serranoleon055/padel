package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.MedioPago;
import com.padel.rankpadel.enums.TipoComprobanteCompra;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una compra a proveedor, tal como vino el papel: un comprobante con varios productos.
 *
 * <p>Antes la compra era de a un producto, así que un remito de doce eran doce gastos
 * sueltos en la rentabilidad del mes, imposibles de reconciliar contra el papel.
 *
 * <p><b>No tiene tabla de renglones.</b> El renglón de la compra ES el
 * {@code MovimientoStock} enlazado a este documento. Duplicarlos en una tabla aparte
 * sería garantizar que algún día no coincidan, y rompería el invariante de V48: el stock
 * de un producto es siempre la suma de sus movimientos.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "documentos_compra")
public class DocumentoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    private TipoComprobanteCompra tipoComprobante;

    private String numero;

    /** La del comprobante: a qué mes pesa la compra. */
    private LocalDate fecha;

    /** Jornada de caja en la que se cargó. No se recalcula nunca. */
    private LocalDate jornada;

    private BigDecimal total;

    /** Null = a cuenta corriente: la mercadería entró pero todavía no se pagó. */
    @Enumerated(EnumType.STRING)
    private MedioPago medio;

    /** El egreso que generó. Es uno solo por compra, aunque traiga doce productos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gasto_id")
    private Gasto gasto;

    private String registradoPor;
    private String notas;
    private LocalDateTime creadoEn;

    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;

    public boolean estaAnulado() {
        return anuladoEn != null;
    }

    /** Si quedó a cuenta corriente del proveedor. */
    public boolean esACredito() {
        return medio == null;
    }
}
