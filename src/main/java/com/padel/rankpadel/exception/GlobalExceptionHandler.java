package com.padel.rankpadel.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("Error inesperado: {}", ex.getMessage(), ex);
        ApiError apiError = ApiError.builder()
                .status(500)
                .error("Internal Server Error")
                .mensaje("Error interno del servidor. Intentá de nuevo más tarde.")
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiError> handleCredenciales(CredencialesInvalidasException ex) {
        ApiError apiError = ApiError.builder()
                .status(401)
                .error("Unauthorized")
                .mensaje(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        ApiError apiError = ApiError.builder()
                .status(404)
                .error("Not Found")
                .mensaje(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ApiError> handleEstadoInvalido(EstadoInvalidoException ex) {
        ApiError apiError = ApiError.builder()
                .status(400)
                .error("Bad Request")
                .mensaje(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.badRequest().body(apiError);
    }

    /**
     * Body ilegible: JSON roto, un enum inexistente, una fecha mal escrita o texto que
     * no es UTF-8. Es culpa del cliente; devolverlo como 500 tapaba los errores reales
     * del servidor en el monitoreo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleBodyIlegible(HttpMessageNotReadableException ex) {
        log.warn("Body inválido: {}", ex.getMessage());
        return badRequest("El cuerpo de la petición no es válido. Revisá el formato de los datos enviados.");
    }

    /** Parámetro con el tipo equivocado (?fecha=ayer en vez de una fecha). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return badRequest("El parámetro '" + ex.getName() + "' tiene un valor inválido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleParametroFaltante(MissingServletRequestParameterException ex) {
        return badRequest("Falta el parámetro '" + ex.getParameterName() + "'.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMetodoNoSoportado(HttpRequestMethodNotSupportedException ex) {
        ApiError apiError = ApiError.builder()
                .status(405)
                .error("Method Not Allowed")
                .mensaje("El método " + ex.getMethod() + " no está permitido en esta dirección.")
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(apiError);
    }

    /**
     * Dos personas tocaron lo mismo al mismo tiempo. Pasa sobre todo con el stock: dos
     * ventas simultáneas de la última unidad. No es un error del que la carga: el dato
     * cambió abajo, hay que volver a mirarlo.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConflicto(ObjectOptimisticLockingFailureException ex) {
        log.warn("Escritura concurrente: {}", ex.getMessage());
        ApiError apiError = ApiError.builder()
                .status(409)
                .error("Conflict")
                .mensaje("Alguien más modificó esto hace un segundo. Actualizá la pantalla y volvé a intentar.")
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    private ResponseEntity<ApiError> badRequest(String mensaje) {
        ApiError apiError = ApiError.builder()
                .status(400)
                .error("Bad Request")
                .mensaje(mensaje)
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String mensajes = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ApiError apiError = ApiError.builder()
                .status(400)
                .error("Bad Request")
                .mensaje(mensajes)
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.badRequest().body(apiError);
    }
}