package com.padel.rankpadel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * De qué IP viene una request, para los límites por IP.
 *
 * <p>El detalle que importa: {@code X-Forwarded-For} es una lista y cada proxy AGREGA su
 * dato al final. El primer elemento es el que puso el cliente, así que <b>lo elige él</b>:
 * tomándolo como identidad, cualquiera manda un valor distinto en cada intento y estrena
 * un cubo de rate limit nuevo cada vez. El límite de login existía pero no servía de nada.
 *
 * <p>El valor confiable es el que agregó <b>nuestro</b> proxy, contando desde el final.
 * Con un solo proxy adelante (Render, Railway) es el último. Si algún día se pone otro
 * (Cloudflare delante del backend), hay que subir {@code app.security.proxies-confiables}:
 * si queda corto se puede falsear la IP, y si queda largo todo el tráfico comparte la IP
 * del proxy y se limitan entre sí usuarios que no tienen nada que ver.
 */
@Component
public class ClienteIp {

    @Value("${app.security.proxies-confiables:1}")
    private int proxiesConfiables;

    public String de(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] saltos = forwarded.split(",");
        int indice = saltos.length - Math.max(1, proxiesConfiables);
        String ip = saltos[Math.max(0, indice)].trim();
        return ip.isEmpty() ? request.getRemoteAddr() : ip;
    }
}
