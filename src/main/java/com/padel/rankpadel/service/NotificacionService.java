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
