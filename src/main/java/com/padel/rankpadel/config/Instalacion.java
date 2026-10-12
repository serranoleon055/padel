package com.padel.rankpadel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.padel.rankpadel.enums.ModoInstalacion;

/**
 * El modo con el que arrancó esta instalación ({@code APP_MODO}). Se fija por deploy y
 * no cambia en caliente: un club y un organizador son clientes distintos, cada uno con
 * su base.
 *
 * <p>Por defecto es {@link ModoInstalacion#CLUB}, para que los clubes que ya están en
 * producción no tengan que tocar ninguna variable.
 */
@Component
public class Instalacion {

    private final ModoInstalacion modo;

    public Instalacion(@Value("${app.modo:CLUB}") String modo) {
        this.modo = ModoInstalacion.valueOf(modo.trim().toUpperCase());
    }

    public ModoInstalacion getModo() {
        return modo;
    }

    public boolean esOrganizador() {
        return modo == ModoInstalacion.ORGANIZADOR;
    }
}
