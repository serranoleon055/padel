package com.padel.rankpadel.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@DisplayName("GlobalExceptionHandler - errores del cliente no son 500")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Un body ilegible devuelve 400, no 500")
    void bodyIlegible_devuelve400() {
        ResponseEntity<ApiError> respuesta = handler.handleBodyIlegible(
                new HttpMessageNotReadableException("JSON parse error", (org.springframework.http.HttpInputMessage) null));

        // Devolverlo como 500 tapaba los errores reales del servidor en el monitoreo.
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().getMensaje()).contains("no es válido");
    }

    @Test
    @DisplayName("Un método HTTP equivocado devuelve 405")
    void metodoEquivocado_devuelve405() {
        ResponseEntity<ApiError> respuesta = handler.handleMetodoNoSoportado(
                new HttpRequestMethodNotSupportedException("POST"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getBody().getMensaje()).contains("POST");
    }

    @Test
    @DisplayName("Un parámetro con tipo inválido devuelve 400 y nombra el parámetro")
    void parametroInvalido_devuelve400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "ayer", java.time.LocalDate.class, "fecha", (MethodParameter) null, null);

        ResponseEntity<ApiError> respuesta = handler.handleTipoInvalido(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().getMensaje()).contains("fecha");
    }

    @Test
    @DisplayName("Un parámetro obligatorio faltante devuelve 400 y dice cuál falta")
    void parametroFaltante_devuelve400() {
        ResponseEntity<ApiError> respuesta = handler.handleParametroFaltante(
                new MissingServletRequestParameterException("canchaId", "Long"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().getMensaje()).contains("canchaId");
    }

    @Test
    @DisplayName("Un error inesperado sigue siendo 500 y no filtra el detalle interno")
    void errorInesperado_devuelve500SinDetalle() {
        ResponseEntity<ApiError> respuesta = handler.handleGeneral(
                new IllegalStateException("password=hunter2 en la connection string"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody().getMensaje()).doesNotContain("hunter2");
    }
}
