package com.padel.rankpadel.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import com.padel.rankpadel.entity.ConfiguracionSede;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.ConfiguracionSedeRepository;

@Service
public class MercadoPagoService {

    @Value("${app.mercadopago.access-token:}")
    private String accessTokenEnv;

    private final ConfiguracionSedeRepository configuracionSedeRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MercadoPagoService(ConfiguracionSedeRepository configuracionSedeRepository) {
        this.configuracionSedeRepository = configuracionSedeRepository;
    }

    private String resolverAccessToken() {
        String desdeConfig = configuracionSedeRepository.findById(1L)
                .map(ConfiguracionSede::getMercadoPagoAccessToken)
                .orElse(null);
        if (desdeConfig != null && !desdeConfig.isBlank()) {
            return desdeConfig;
        }
        return accessTokenEnv;
    }

    public record PreferenciaCreada(String id, String initPoint) {
    }

    public record PagoMercadoPago(String id, String estado, String referenciaExterna) {
    }

    public PreferenciaCreada crearPreferencia(String referenciaExterna, String titulo, BigDecimal monto,
            String urlExito, String urlPendiente, String urlError, String notificationUrl) {
        aplicarToken();
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title(titulo)
                .quantity(1)
                .unitPrice(monto)
                .currencyId("ARS")
                .build();
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(urlExito)
                .pending(urlPendiente)
                .failure(urlError)
                .build();
        PreferenceRequest.PreferenceRequestBuilder solicitud = PreferenceRequest.builder()
                .items(List.of(item))
                .externalReference(referenciaExterna)
                .backUrls(backUrls);
        if (urlExito != null && urlExito.startsWith("https")) {
            solicitud.autoReturn("approved");
        }
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            solicitud.notificationUrl(notificationUrl);
        }
        try {
            Preference preferencia = new PreferenceClient().create(solicitud.build());
            return new PreferenciaCreada(preferencia.getId(), preferencia.getInitPoint());
        } catch (MPApiException e) {
            throw new EstadoInvalidoException(
                    "No se pudo iniciar el pago con Mercado Pago: " + e.getApiResponse().getContent());
        } catch (MPException e) {
            throw new EstadoInvalidoException("No se pudo iniciar el pago con Mercado Pago: " + e.getMessage());
        }
    }

    public Optional<PagoMercadoPago> buscarPagoAprobado(String referenciaExterna) {
        JsonNode raiz = consultar("https://api.mercadopago.com/v1/payments/search?external_reference="
                + URLEncoder.encode(referenciaExterna, StandardCharsets.UTF_8));
        for (JsonNode pago : raiz.path("results")) {
            if ("approved".equals(pago.path("status").asText())) {
                return Optional.of(aPagoMercadoPago(pago));
            }
        }
        return Optional.empty();
    }

    public Optional<PagoMercadoPago> obtenerPago(Long pagoMercadoPagoId) {
        try {
            return Optional.of(aPagoMercadoPago(consultar("https://api.mercadopago.com/v1/payments/" + pagoMercadoPagoId)));
        } catch (EstadoInvalidoException e) {
            return Optional.empty();
        }
    }

    private JsonNode consultar(String url) {
        String accessToken = resolverAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new EstadoInvalidoException("Los pagos online no están configurados");
        }
        try {
            HttpRequest solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> respuesta = httpClient.send(solicitud, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2) {
                throw new EstadoInvalidoException("Mercado Pago respondió " + respuesta.statusCode() + ": " + respuesta.body());
            }
            return objectMapper.readTree(respuesta.body());
        } catch (IOException e) {
            throw new EstadoInvalidoException("No se pudo consultar Mercado Pago: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EstadoInvalidoException("No se pudo consultar Mercado Pago: " + e.getMessage());
        }
    }

    private PagoMercadoPago aPagoMercadoPago(JsonNode pago) {
        return new PagoMercadoPago(pago.path("id").asText(), pago.path("status").asText(),
                pago.path("external_reference").asText());
    }

    private void aplicarToken() {
        String accessToken = resolverAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new EstadoInvalidoException("Los pagos online no están configurados");
        }
        MercadoPagoConfig.setAccessToken(accessToken);
    }
}
