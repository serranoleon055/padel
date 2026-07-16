package com.padel.rankpadel.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valida el header x-signature de los webhooks de Mercado Pago
 * (HMAC-SHA256 sobre el manifest id/request-id/ts, según la doc oficial).
 * Si MERCADO_PAGO_WEBHOOK_SECRET no está configurado, no valida (compatible
 * con el comportamiento actual: cada notificación se re-consulta a la API
 * de MP igual, así que una firma falsa nunca puede aprobar un pago).
 */
@Component
public class MercadoPagoWebhookValidator {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookValidator.class);

    @Value("${app.mercadopago.webhook-secret:}")
    private String secret;

    public boolean esValida(String xSignature, String xRequestId, String dataId) {
        if (secret == null || secret.isBlank()) {
            return true;
        }
        if (xSignature == null || xSignature.isBlank()) {
            log.warn("[MP webhook] Notificación sin x-signature rechazada");
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String clave = kv[0].trim();
            if ("ts".equals(clave)) {
                ts = kv[1].trim();
            } else if ("v1".equals(clave)) {
                v1 = kv[1].trim();
            }
        }
        if (ts == null || v1 == null) {
            log.warn("[MP webhook] x-signature con formato inesperado");
            return false;
        }

        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId.toLowerCase()).append(";");
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(";");
        }
        manifest.append("ts:").append(ts).append(";");

        String esperada = hmacSha256Hex(manifest.toString());
        boolean valida = esperada != null && MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
        if (!valida) {
            log.warn("[MP webhook] Firma inválida rechazada (dataId={})", dataId);
        }
        return valida;
    }

    private String hmacSha256Hex(String mensaje) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("[MP webhook] No se pudo calcular la firma: {}", e.getMessage());
            return null;
        }
    }
}
