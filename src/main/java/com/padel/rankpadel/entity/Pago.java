package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.padel.rankpadel.enums.ConceptoPago;
import com.padel.rankpadel.enums.EstadoPago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "pagos")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ConceptoPago concepto;

    @Enumerated(EnumType.STRING)
    private EstadoPago estado;

    private BigDecimal montoTotal;
    private BigDecimal montoSenia;
    private Integer porcentajeSenia;

    private String referenciaExterna;
    private String preferenciaId;

    @Column(name = "pago_mercado_pago_id")
    private String pagoMercadoPagoId;

    private String clienteNombre;
    private String clienteTelefono;
    private LocalDateTime creadoEn;
    private LocalDateTime pagadoEn;

    /**
     * Jornada del club en que se acreditó la seña. Null mientras el pago está pendiente:
     * todavía no entró a ninguna caja. Se estampa al aprobarlo, junto con
     * {@link #pagadoEn}, para que la seña de la 1 AM se cuente en el arqueo de esa noche
     * y no en el del día siguiente (ver V55).
     */
    private LocalDate jornada;
}
