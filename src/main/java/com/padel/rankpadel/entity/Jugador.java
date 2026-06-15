package com.padel.rankpadel.entity;

import java.time.LocalDate;

import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.enums.PosicionJuego;
import com.padel.rankpadel.util.NormalizadorTexto;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "jugadores")
public class Jugador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String apellido;

    @Enumerated(EnumType.STRING)
    private Genero genero;

    private String fotoUrl;
    private LocalDate fechaRegistro;
    @Builder.Default
    private boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    private String telefono;
    private LocalDate fechaNacimiento;
    private String nombreNormalizado;

    @Enumerated(EnumType.STRING)
    private PosicionJuego posicionJuego;

    @PrePersist
    @PreUpdate
    private void calcularNombreNormalizado() {
        this.nombreNormalizado = NormalizadorTexto.normalizarNombre(nombre, apellido);
    }
}
