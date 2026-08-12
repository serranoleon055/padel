package com.padel.rankpadel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Envío de mails en segundo plano. Está aparte de {@link NotificacionService} porque
 * {@code @Async} solo funciona a través del proxy de Spring (una llamada dentro de la
 * misma clase se ejecutaría igual en el hilo del request).
 *
 * <p>Si el SMTP no está configurado, no hace nada: el sistema tiene que seguir
 * funcionando aunque el club todavía no haya cargado un servidor de correo.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${app.notificaciones.remitente:}")
    private String remitente;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public boolean configurado() {
        return host != null && !host.isBlank() && remitente != null && !remitente.isBlank();
    }

    @Async
    public void enviar(String destino, String asunto, String cuerpo) {
        if (!configurado()) {
            log.debug("[notificación] SMTP sin configurar, no se envía: {}", asunto);
            return;
        }
        try {
            enviarAhora(destino, asunto, cuerpo);
        } catch (RuntimeException e) {
            // Que falle un mail nunca puede tumbar una reserva ni un pago.
            log.error("[notificación] No se pudo enviar '{}' a {}: {}", asunto, destino, e.getMessage());
        }
    }

    /**
     * Envía en el hilo del request y deja propagar el error. Lo usa el mail de prueba del
     * panel: ahí el club necesita saber si funcionó, no que falle en silencio.
     */
    public void enviarAhora(String destino, String asunto, String cuerpo) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("No hay un servidor de correo configurado");
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destino);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        sender.send(mensaje);
        log.info("[notificación] Enviada a {}: {}", destino, asunto);
    }
}
