package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.enums.MedioPago;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una venta en el mostrador. Entra al cierre de caja del día por el mismo camino que el
 * cobro de un turno: lo que se cobró en efectivo tiene que estar en el cajón.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "ventas")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fecha;

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private MedioPago medio;

    /** Opcional: para que la compra quede en la ficha de la persona. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /** Opcional: la consumición que se sumó a un turno concreto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    private String registradoPor;
    private String notas;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VentaItem> items = new ArrayList<>();

    /**
     * Anulación. Baja lógica, igual que en {@link Cobro}: la venta sale de la caja y de
     * las estadísticas, la mercadería vuelve al stock con su propio movimiento, y el
     * renglón queda para poder explicar después qué pasó.
     */
    private LocalDateTime anuladoEn;
    private String anuladoPor;
    private String motivoAnulacion;

    public boolean estaAnulada() {
        return anuladoEn != null;
    }
}
