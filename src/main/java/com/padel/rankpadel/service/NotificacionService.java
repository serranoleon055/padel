package com.padel.rankpadel.service;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.response.EstadoNotificacionesResponse;
import com.padel.rankpadel.dto.response.GeneracionTurnosFijosResponse.Conflicto;
import com.padel.rankpadel.entity.ConfiguracionSede;
import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.ConfiguracionSedeRepository;
import com.padel.rankpadel.util.MontosReserva;

import lombok.RequiredArgsConstructor;

/**
 * Avisos al club por mail. Sin esto el club solo se entera de una solicitud si tiene
 * el panel abierto, que es justo lo que el sistema viene a resolver.
 *
 * <p>El destinatario es el mail cargado en Configuración de sede; si no hay ninguno,
 * cae en {@code NOTIFICACIONES_DESTINO}. Si no hay ni uno ni otro, no se envía nada.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ConfiguracionSedeRepository configuracionSedeRepository;
    private final EmailSender emailSender;

    @Value("${app.notificaciones.destino:}")
    private String destinoPorDefecto;

    @Transactional(readOnly = true)
    public void avisarNuevaSolicitudReserva(Reserva reserva) {
        String destino = destino();
        if (destino == null || reserva == null) {
            return;
        }
        String cuerpo = """
                Entró una solicitud de turno.

                Cancha:   %s
                Día:      %s de %s a %s
                Cliente:  %s
                Teléfono: %s
                Código:   %s

                Vence:    %s

                Entrá al panel para confirmarla o rechazarla.
                """.formatted(
                nombreCancha(reserva),
                reserva.getFecha() != null ? reserva.getFecha().format(FECHA) : "-",
                reserva.getHoraInicio() != null ? reserva.getHoraInicio().format(HORA) : "-",
                reserva.getHoraFin() != null ? reserva.getHoraFin().format(HORA) : "-",
                reserva.getClienteNombre(),
                reserva.getClienteTelefono(),
                reserva.getCodigo(),
                reserva.getExpiraEn() != null
                        ? reserva.getExpiraEn().format(FECHA) + " " + reserva.getExpiraEn().format(HORA)
                        : "-");

        emailSender.enviar(destino,
                "Nuevo turno pedido — " + reserva.getClienteNombre(), cuerpo);
    }

    /**
     * Aviso al CLUB de que un jugador canceló desde su enlace.
     *
     * <p>Sin esto, la cancha se libera y nadie se entera: el club sigue creyendo que esa
     * hora está vendida y no la ofrece. Va con la seña, porque si había plata cobrada
     * alguien tiene que decidir qué hacer con ella.
     */
    @Transactional(readOnly = true)
    public void avisarCancelacionDelJugador(Reserva reserva) {
        String destino = destino();
        if (destino == null || reserva == null) {
            return;
        }
        String cuerpo = """
                Un jugador canceló su turno desde la web. La cancha quedó libre.

                Cancha:   %s
                Día:      %s de %s a %s
                Cliente:  %s
                Teléfono: %s
                Código:   %s
                Seña cobrada: %s

                Si había seña, el sistema NO la devuelve solo.
                """.formatted(
                nombreCancha(reserva),
                reserva.getFecha() != null ? reserva.getFecha().format(FECHA) : "-",
                reserva.getHoraInicio() != null ? reserva.getHoraInicio().format(HORA) : "-",
                reserva.getHoraFin() != null ? reserva.getHoraFin().format(HORA) : "-",
                reserva.getClienteNombre(),
                reserva.getClienteTelefono(),
                reserva.getCodigo(),
                MontosReserva.seniaPagada(reserva).compareTo(java.math.BigDecimal.ZERO) > 0
                        ? "$" + MontosReserva.seniaPagada(reserva)
                        : "no");

        emailSender.enviar(destino,
                "Turno cancelado por el jugador — " + reserva.getClienteNombre(), cuerpo);
    }

    /**
     * Comprobante para el JUGADOR, con el enlace de su turno.
     *
     * <p>Es el primer aviso que el sistema le manda a alguien que no es del club. Hasta
     * acá, el que reservaba se quedaba con un código en la pantalla: si cerraba la pestaña
     * no le quedaba nada, y para cualquier cosa tenía que llamar.
     */
    @Transactional(readOnly = true)
    public void enviarComprobanteAlJugador(Reserva reserva, String enlace) {
        String destino = mailDelJugador(reserva);
        if (destino == null) {
            return;
        }
        String cuerpo = """
                %s, tu turno quedó anotado.

                Cancha: %s
                Día:    %s de %s a %s
                Código: %s
                Estado: %s

                Podés ver o cancelar tu turno desde acá:
                %s

                Guardá este mail: es tu comprobante.
                """.formatted(
                reserva.getClienteNombre(),
                nombreCancha(reserva),
                reserva.getFecha() != null ? reserva.getFecha().format(FECHA) : "-",
                reserva.getHoraInicio() != null ? reserva.getHoraInicio().format(HORA) : "-",
                reserva.getHoraFin() != null ? reserva.getHoraFin().format(HORA) : "-",
                reserva.getCodigo(),
                estadoLegible(reserva),
                enlace);

        emailSender.enviar(destino, "Tu turno — " + nombreCancha(reserva), cuerpo);
    }

    /** Recordatorio del turno de mañana, al jugador que dejó su mail. */
    @Transactional(readOnly = true)
    public void recordarTurnoAlJugador(Reserva reserva, String enlace) {
        String destino = mailDelJugador(reserva);
        if (destino == null) {
            return;
        }
        String cuerpo = """
                %s, te recordamos tu turno.

                Cancha: %s
                Día:    %s a las %s
                Código: %s

                Si no vas a poder venir, avisanos desde acá:
                %s
                """.formatted(
                reserva.getClienteNombre(),
                nombreCancha(reserva),
                reserva.getFecha() != null ? reserva.getFecha().format(FECHA) : "-",
                reserva.getHoraInicio() != null ? reserva.getHoraInicio().format(HORA) : "-",
                reserva.getCodigo(),
                enlace);

        emailSender.enviar(destino, "Recordatorio de tu turno", cuerpo);
    }

    /**
     * A dónde escribirle al jugador: primero lo que dejó al reservar (que es de ese día) y
     * si no, lo que tenga la ficha. Sin mail no se manda nada y no pasa nada: reservar
     * nunca lo exigió.
     */
    private String mailDelJugador(Reserva reserva) {
        if (reserva == null) {
            return null;
        }
        if (reserva.getClienteEmail() != null && !reserva.getClienteEmail().isBlank()) {
            return reserva.getClienteEmail().trim();
        }
        if (reserva.getCliente() != null && reserva.getCliente().getEmail() != null
                && !reserva.getCliente().getEmail().isBlank()) {
            return reserva.getCliente().getEmail().trim();
        }
        return null;
    }

    private String estadoLegible(Reserva reserva) {
        if (reserva.getEstado() == null) {
            return "-";
        }
        return switch (reserva.getEstado()) {
            case PENDIENTE -> "a confirmar por el club";
            case CONFIRMADA -> "confirmado";
            default -> reserva.getEstado().name().toLowerCase();
        };
    }

    @Transactional(readOnly = true)
    public void avisarNuevaInscripcion(SolicitudInscripcion solicitud) {
        String destino = destino();
        if (destino == null || solicitud == null) {
            return;
        }
        String cuerpo = """
                Entró una inscripción a torneo.

                Torneo:    %s
                Categoría: %s
                Pareja:    %s %s / %s %s
                Contacto:  %s
                Seña:      %s

                Entrá al panel para aprobarla o rechazarla.
                """.formatted(
                solicitud.getTorneo() != null ? solicitud.getTorneo().getNombre() : "-",
                solicitud.getCategoria() != null ? solicitud.getCategoria().getNombre() : "-",
                solicitud.getJugador1Nombre(), solicitud.getJugador1Apellido(),
                solicitud.getJugador2Nombre(), solicitud.getJugador2Apellido(),
                solicitud.getTelefonoContacto(),
                solicitud.isPagada() ? "pagada" : "sin pagar");

        emailSender.enviar(destino, "Nueva inscripción a torneo", cuerpo);
    }

    /**
     * Caso grave: el pago entró pero el turno ya no estaba. Hay plata cobrada sin cancha
     * entregada y alguien del club tiene que devolverla a mano.
     */
    @Transactional(readOnly = true)
    public void avisarPagoSinTurno(Pago pago, List<Reserva> perdidas) {
        String destino = destino();
        if (destino == null || pago == null) {
            return;
        }
        StringBuilder detalle = new StringBuilder();
        for (Reserva reserva : perdidas) {
            detalle.append("  · ").append(nombreCancha(reserva))
                    .append(" — ").append(reserva.getFecha() != null ? reserva.getFecha().format(FECHA) : "-")
                    .append(" ").append(reserva.getHoraInicio() != null ? reserva.getHoraInicio().format(HORA) : "-")
                    .append(" (").append(reserva.getEstado()).append(")\n");
        }
        String cuerpo = """
                ATENCIÓN: se cobró una seña por un turno que ya no estaba disponible.

                Cliente:   %s
                Teléfono:  %s
                Monto:     $%s
                Pago MP:   %s
                Referencia:%s

                Turnos que no se pudieron confirmar:
                %s
                Hay que contactar al cliente y devolverle la seña.
                """.formatted(
                pago.getClienteNombre(), pago.getClienteTelefono(),
                pago.getMontoSenia(), pago.getPagoMercadoPagoId(), pago.getReferenciaExterna(),
                detalle);

        emailSender.enviar(destino, "URGENTE: seña cobrada sin turno disponible", cuerpo);
    }

    /**
     * Un turno fijo que no se pudo generar porque el horario estaba tomado. El club tiene
     * que reubicar a alguien, y es mejor enterarse ahora que cuando lleguen los dos.
     */
    public void avisarConflictosTurnosFijos(List<Conflicto> conflictos) {
        String destino = destino();
        if (destino == null || conflictos.isEmpty()) {
            return;
        }
        StringBuilder detalle = new StringBuilder();
        for (Conflicto conflicto : conflictos) {
            detalle.append("  · ").append(conflicto.getClienteNombre())
                    .append(" — ").append(conflicto.getCanchaNombre())
                    .append(" ").append(conflicto.getFecha().format(FECHA))
                    .append(" ").append(conflicto.getHoraInicio())
                    .append(" (").append(conflicto.getMotivo()).append(")\n");
        }
        String cuerpo = """
                Estos turnos fijos no se pudieron generar porque el horario ya estaba ocupado:

                %s
                Hay que reubicar el turno o avisarle al cliente.
                """.formatted(detalle);

        emailSender.enviar(destino, "Turnos fijos con conflicto de horario", cuerpo);
    }

    /** Estado de los avisos, para mostrarlo en Configuración de sede. */
    @Transactional(readOnly = true)
    public EstadoNotificacionesResponse estado() {
        String delClub = emailDelClub();
        String destino = delClub != null ? delClub : porDefecto();
        return EstadoNotificacionesResponse.builder()
                .servidorConfigurado(emailSender.configurado())
                .destino(destino)
                .origenDestino(destino == null ? null : (delClub != null ? "sede" : "variable"))
                .activo(emailSender.configurado() && destino != null)
                .build();
    }

    /**
     * Mail de prueba. Va sincrónico y deja subir el error: el club aprieta el botón para
     * confirmar que los avisos llegan, y un "enviado" que en realidad falló sería peor que
     * no tener el botón.
     */
    @Transactional(readOnly = true)
    public String enviarPrueba() {
        if (!emailSender.configurado()) {
            throw new EstadoInvalidoException(
                    "No hay un servidor de correo configurado en el servidor. "
                            + "Hasta que se cargue, los avisos no salen.");
        }
        String destino = destino();
        if (destino == null) {
            throw new EstadoInvalidoException(
                    "Cargá el mail del club en Configuración de sede para recibir los avisos.");
        }
        try {
            emailSender.enviarAhora(destino, "Prueba de avisos del sistema", """
                    Los avisos por mail están funcionando.

                    A esta casilla van a llegar:
                      · las solicitudes de turno nuevas
                      · las inscripciones a torneos
                      · los turnos fijos que no se pudieron agendar
                      · las señas cobradas por turnos que ya no estaban disponibles

                    Si recibiste este mensaje, no hay nada más que configurar.
                    """);
        } catch (RuntimeException e) {
            throw new EstadoInvalidoException("No se pudo enviar el mail: " + e.getMessage());
        }
        return destino;
    }

    private String emailDelClub() {
        String email = configuracionSedeRepository.findById(1L)
                .map(ConfiguracionSede::getEmail)
                .orElse(null);
        return email != null && !email.isBlank() ? email.trim() : null;
    }

    private String porDefecto() {
        return destinoPorDefecto != null && !destinoPorDefecto.isBlank() ? destinoPorDefecto.trim() : null;
    }

    private String nombreCancha(Reserva reserva) {
        return reserva.getCancha() != null ? reserva.getCancha().getNombre() : "-";
    }

    private String destino() {
        if (!emailSender.configurado()) {
            return null;
        }
        String delClub = emailDelClub();
        return delClub != null ? delClub : porDefecto();
    }
}
