package com.padel.rankpadel.enums;

/**
 * Quién es el que entró al panel.
 *
 * <p>La separación existe por la caja: el registro de quién cobró y quién anuló no sirve
 * de nada si todos entran con el mismo usuario. El del mostrador tiene que poder trabajar
 * —cobrar, vender, atender turnos— sin poder ver la rentabilidad del club, tocar los
 * gastos, ni reabrir un arqueo ya firmado.
 */
public enum RolUsuario {

    /** Acceso completo. */
    DUENIO,

    /** Atiende: turnos, cobros y ventas. No ve números del negocio ni configura nada. */
    MOSTRADOR
}
