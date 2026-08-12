package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.ImportacionResponse;
import com.padel.rankpadel.entity.Cliente;
import com.padel.rankpadel.entity.Jugador;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.ClienteRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.util.LectorCsv;
import com.padel.rankpadel.util.NormalizadorTelefono;
import com.padel.rankpadel.util.NormalizadorTexto;

import lombok.RequiredArgsConstructor;

/**
 * Importación de jugadores y clientes desde la planilla que el club ya tiene.
 *
 * <p>Es lo que resuelve el primer día: un club con 200 jugadores en un Excel no arranca
 * si tiene que cargarlos a mano, y ese es justo el momento en que se decide si el sistema
 * se usa o se abandona.
 *
 * <p>Siempre se puede pedir primero la vista previa. El club ve fila por fila qué va a
 * pasar antes de que se escriba nada: importar 200 registros a ciegas y descubrir después
 * que se duplicaron es peor que no importar.
 */
@Service
@RequiredArgsConstructor
public class ImportacionService {

    /** Un archivo más grande que esto no es la planilla de un club, es un error. */
    private static final int MAXIMO_FILAS = 2000;

    private final JugadorRepository jugadorRepository;
    private final ClienteRepository clienteRepository;

    /**
     * Jugadores de torneo. El duplicado se detecta por teléfono normalizado y, si no hay
     * teléfono, por nombre y apellido normalizados —que es como ya los compara el resto
     * del sistema—.
     */
    @Transactional
    public ImportacionResponse importarJugadores(String csv, boolean vistaPrevia) {
        List<Map<String, String>> filas = leer(csv);
        List<ImportacionResponse.Fila> detalle = new ArrayList<>();
        // Los repetidos dentro del mismo archivo también son repetidos: si la planilla
        // trae dos veces al mismo, no se puede crear dos veces.
        Set<String> vistosTelefono = new HashSet<>();
        Set<String> vistosNombre = new HashSet<>();
        int nuevos = 0;
        int repetidos = 0;
        int conError = 0;

        for (int i = 0; i < filas.size(); i++) {
            Map<String, String> fila = filas.get(i);
            int numero = i + 2; // +1 por la cabecera, +1 porque el Excel cuenta desde 1

            String nombre = LectorCsv.valor(fila, "nombre", "nombres");
            String apellido = LectorCsv.valor(fila, "apellido", "apellidos");
            String telefono = LectorCsv.valor(fila, "telefono", "celular", "whatsapp");
            String etiqueta = nombre != null ? (nombre + (apellido != null ? " " + apellido : "")) : "";

            if (nombre == null) {
                detalle.add(error(numero, etiqueta, telefono, "Falta el nombre"));
                conError++;
                continue;
            }

            String telNormalizado = NormalizadorTelefono.normalizar(telefono);
            String nombreNormalizado = NormalizadorTexto.normalizarNombre(nombre, apellido);

            String choque = duplicadoJugador(telNormalizado, nombreNormalizado, vistosTelefono, vistosNombre);
            if (choque != null) {
                detalle.add(ImportacionResponse.Fila.builder()
                        .numero(numero).nombre(etiqueta).telefono(telefono)
                        .resultado("REPETIDO").detalle(choque).build());
                repetidos++;
                continue;
            }

            if (telNormalizado != null) {
                vistosTelefono.add(telNormalizado);
            }
            vistosNombre.add(nombreNormalizado);
            nuevos++;
            detalle.add(ImportacionResponse.Fila.builder()
                    .numero(numero).nombre(etiqueta).telefono(telefono)
                    .resultado("NUEVO").build());

            if (!vistaPrevia) {
                jugadorRepository.save(Jugador.builder()
                        .nombre(nombre)
                        .apellido(apellido)
                        .telefono(telefono)
                        .genero(genero(LectorCsv.valor(fila, "genero", "sexo")))
                        .fechaRegistro(LocalDate.now())
                        .activo(true)
                        .build());
            }
        }

        return resumen(vistaPrevia, filas.size(), nuevos, repetidos, conError, detalle);
    }

    /**
     * Clientes que alquilan cancha. Acá la identidad es SOLO el teléfono: es la regla de
     * toda la ficha de cliente, y sin teléfono no hay a quién enganchar las reservas.
     */
    @Transactional
    public ImportacionResponse importarClientes(String csv, boolean vistaPrevia) {
        List<Map<String, String>> filas = leer(csv);
        List<ImportacionResponse.Fila> detalle = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        int nuevos = 0;
        int repetidos = 0;
        int conError = 0;

        for (int i = 0; i < filas.size(); i++) {
            Map<String, String> fila = filas.get(i);
            int numero = i + 2;

            String nombre = LectorCsv.valor(fila, "nombre", "nombres", "cliente");
            String telefono = LectorCsv.valor(fila, "telefono", "celular", "whatsapp");
            String normalizado = NormalizadorTelefono.normalizar(telefono);

            if (nombre == null) {
                detalle.add(error(numero, "", telefono, "Falta el nombre"));
                conError++;
                continue;
            }
            if (normalizado == null) {
                detalle.add(error(numero, nombre, telefono,
                        "El teléfono no es válido, y es lo que identifica al cliente"));
                conError++;
                continue;
            }
            if (!vistos.add(normalizado) || clienteRepository.findByTelefonoNormalizado(normalizado).isPresent()) {
                detalle.add(ImportacionResponse.Fila.builder()
                        .numero(numero).nombre(nombre).telefono(telefono)
                        .resultado("REPETIDO").detalle("Ese teléfono ya tiene ficha").build());
                repetidos++;
                continue;
            }

            nuevos++;
            detalle.add(ImportacionResponse.Fila.builder()
                    .numero(numero).nombre(nombre).telefono(telefono)
                    .resultado("NUEVO").build());

            if (!vistaPrevia) {
                clienteRepository.save(Cliente.builder()
                        .nombre(nombre.trim())
                        .telefono(telefono.trim())
                        .email(LectorCsv.valor(fila, "email", "correo", "mail"))
                        .creadoEn(LocalDateTime.now())
                        .build());
            }
        }

        return resumen(vistaPrevia, filas.size(), nuevos, repetidos, conError, detalle);
    }

    /** Con quién choca la fila, o null si entra limpia. */
    private String duplicadoJugador(String telNormalizado, String nombreNormalizado,
            Set<String> vistosTelefono, Set<String> vistosNombre) {
        if (telNormalizado != null && vistosTelefono.contains(telNormalizado)) {
            return "Ese teléfono ya aparece antes en el archivo";
        }
        if (vistosNombre.contains(nombreNormalizado)) {
            return "Ese nombre ya aparece antes en el archivo";
        }
        if (jugadorRepository.existsByActivoTrueAndNombreNormalizado(nombreNormalizado)) {
            return "Ya hay un jugador con ese nombre";
        }
        return null;
    }

    private Genero genero(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim().toUpperCase();
        if (limpio.startsWith("M") || limpio.startsWith("V") || limpio.startsWith("H")) {
            return Genero.MASCULINO;
        }
        return limpio.startsWith("F") ? Genero.FEMENINO : null;
    }

    private List<Map<String, String>> leer(String csv) {
        List<Map<String, String>> filas;
        try {
            filas = LectorCsv.leer(csv);
        } catch (IllegalArgumentException e) {
            throw new EstadoInvalidoException(e.getMessage());
        }
        if (filas.isEmpty()) {
            throw new EstadoInvalidoException("El archivo tiene la cabecera pero ninguna fila con datos");
        }
        if (filas.size() > MAXIMO_FILAS) {
            throw new EstadoInvalidoException(
                    "El archivo tiene " + filas.size() + " filas. El máximo por importación es " + MAXIMO_FILAS + ".");
        }
        return filas;
    }

    private ImportacionResponse.Fila error(int numero, String nombre, String telefono, String detalle) {
        return ImportacionResponse.Fila.builder()
                .numero(numero).nombre(nombre).telefono(telefono)
                .resultado("ERROR").detalle(detalle).build();
    }

    private ImportacionResponse resumen(boolean vistaPrevia, int leidas, int nuevos, int repetidos,
            int conError, List<ImportacionResponse.Fila> detalle) {
        return ImportacionResponse.builder()
                .vistaPrevia(vistaPrevia)
                .filasLeidas(leidas)
                .nuevos(nuevos)
                .repetidos(repetidos)
                .conError(conError)
                .filas(detalle)
                .build();
    }
}
