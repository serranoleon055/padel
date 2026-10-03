package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.padel.rankpadel.enums.EstadoRecuento;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un conteo físico del depósito: la planilla con la que el club compara lo que hay contra
 * lo que dice el sistema.
 *
 * <p>Mientras está en BORRADOR no tocó una sola unidad de stock. Al aplicarlo salen los
 * ajustes de los renglones que tengan diferencia, y la planilla queda congelada: cuánto
 * faltaba, cuánto valía y quién lo aplicó.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "recuentos")
public class Recuento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** El día en que se contó. */
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    private EstadoRecuento estado;

    private String notas;

    private LocalDateTime creadoEn;
    private String creadoPor;

    private LocalDateTime aplicadoEn;
    private String aplicadoPor;

    /** Lo que valía lo que faltó, al costo congelado de cada renglón. Null hasta aplicar. */
    private BigDecimal faltanteValorizado;
    /** Lo que valía lo que sobró. Suele ser mercadería que entró sin cargarse. */
    private BigDecimal sobranteValorizado;

    @Builder.Default
    @OneToMany(mappedBy = "recuento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecuentoItem> items = new ArrayList<>();

    public boolean estaAplicado() {
        return estado == EstadoRecuento.APLICADO;
    }
}
