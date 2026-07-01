package com.padel.rankpadel.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class MapsEmbedResolver {

    private static final Pattern COORDS_AT = Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");
    private static final Pattern COORDS_3D4D = Pattern.compile("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public String resolver(String valor) {
        String texto = valor == null ? "" : valor.trim();
        if (texto.isEmpty()) {
            return null;
        }
        if (texto.contains("output=embed") || texto.contains("/maps/embed")) {
            return texto;
        }

        String url = texto;
        if (esUrl(texto) && esShortLink(texto)) {
            url = expandir(texto);
        }

        String coords = extraerCoords(url);
        if (coords != null) {
            return "https://www.google.com/maps?q=" + coords + "&output=embed";
        }
        if (esUrlMaps(url)) {
            return url + (url.contains("?") ? "&" : "?") + "output=embed";
        }
        return "https://www.google.com/maps?q=" + URLEncoder.encode(texto, StandardCharsets.UTF_8) + "&output=embed";
    }

    private String expandir(String url) {
        try {
            HttpRequest solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<Void> respuesta = httpClient.send(solicitud, HttpResponse.BodyHandlers.discarding());
            return respuesta.uri() != null ? respuesta.uri().toString() : url;
        } catch (Exception e) {
            return url;
        }
    }

    private String extraerCoords(String url) {
        Matcher at = COORDS_AT.matcher(url);
        if (at.find()) {
            return at.group(1) + "," + at.group(2);
        }
        Matcher d = COORDS_3D4D.matcher(url);
        if (d.find()) {
            return d.group(1) + "," + d.group(2);
        }
        return null;
    }

    private boolean esUrl(String texto) {
        return texto.startsWith("http://") || texto.startsWith("https://");
    }

    private boolean esShortLink(String texto) {
        return texto.contains("maps.app.goo.gl") || texto.contains("goo.gl/maps") || texto.contains("g.co/kgs");
    }

    private boolean esUrlMaps(String texto) {
        return esUrl(texto) && (texto.contains("google.") && texto.contains("maps") || texto.contains("maps.google."));
    }
}
