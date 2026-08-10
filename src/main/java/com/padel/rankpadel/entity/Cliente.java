package com.padel.rankpadel.entity;

import java.time.LocalDateTime;

import com.padel.rankpadel.util.NormalizadorTelefono;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cliente del club: quien alquila una cancha. La identidad es el teléfono normalizado,
 * así el mismo cliente no se duplica por escribirlo distinto cada vez.
 *
 * <p>Es distinto de {@link Jugador}, que es el jugador de torneos con categoría y
 * ranking. Una misma persona puede ser las dos cosas: se enlazan por
 * {@code Jugador.cliente}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String telefono;
    private String telefonoNormalizado;
    private String email;
    private String notas;
    private LocalDateTime creadoEn;

    @PrePersist
    @PreUpdate
    private void calcularTelefonoNormalizado() {
        this.telefonoNormalizado = NormalizadorTelefono.normalizar(telefono);
    }
}
