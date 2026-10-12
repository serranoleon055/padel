package com.padel.rankpadel.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un club donde se juega el torneo: qué canchas cede, en qué días y horarios, y cuánto
 * se le paga por todo el torneo.
 *
 * <p>Un organizador juega el mismo fin de semana en dos o tres clubes y le debe a cada
 * uno un monto acordado. En un club, el torneo tiene una sola sede (la propia) y el monto
 * queda vacío.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "torneo_sedes")
public class TorneoSede {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lugar_id")
    private Lugar lugar;

    @Builder.Default
    @ManyToMany
    @JoinTable(name = "torneo_sede_canchas", joinColumns = @JoinColumn(name = "torneo_sede_id"),
            inverseJoinColumns = @JoinColumn(name = "cancha_id"))
    private List<Cancha> canchas = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "torneo_sede_franjas", joinColumns = @JoinColumn(name = "torneo_sede_id"))
    @OrderBy("fecha ASC, horaDesde ASC")
    private List<FranjaSede> franjas = new ArrayList<>();

    /** Lo que se acordó pagarle al club por todo el torneo. Null = no se acordó nada. */
    private BigDecimal montoAcordado;

    private String notas;

    @Builder.Default
    @OneToMany(mappedBy = "sede", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha ASC, id ASC")
    private List<PagoSede> pagos = new ArrayList<>();
}
