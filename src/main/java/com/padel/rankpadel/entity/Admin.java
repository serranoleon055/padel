package com.padel.rankpadel.entity;

import com.padel.rankpadel.enums.RolUsuario;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "admins")
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String passwordHash;

    /**
     * Qué puede hacer. Por defecto DUENIO: un usuario creado sin rol explícito tiene que
     * poder entrar a todo, no quedar a medias.
     */
    @Enumerated(EnumType.STRING)
    private RolUsuario rol = RolUsuario.DUENIO;

    public RolUsuario getRol() {
        return rol != null ? rol : RolUsuario.DUENIO;
    }
}
