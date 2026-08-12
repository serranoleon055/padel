package com.padel.rankpadel.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.padel.rankpadel.dto.request.TurnoFijoRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * El campo de duración del abono se llamaba {@code slots} y contaba horarios; ahora son
 * minutos. Al renombrarlo quedó el {@code @Max(6)} viejo y el alta de abonos respondía
 * 400 para las tres duraciones que ofrece el front. Los tests del servicio no lo vieron
 * porque la validación del request corre antes, en la capa web.
 */
@DisplayName("TurnoFijoRequest - la duración se valida en minutos")
class TurnoFijoRequestValidacionTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrir() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    private TurnoFijoRequest abonoDe(Integer duracionMin) {
        return TurnoFijoRequest.builder()
                .canchaId(1L)
                .diaSemana(2)
                .horaInicio(LocalTime.of(21, 0))
                .duracionMin(duracionMin)
                .clienteNombre("Grupo del martes")
                .clienteTelefono("3855100200")
                .vigenteDesde(LocalDate.now())
                .build();
    }

    @ParameterizedTest
    @ValueSource(ints = { 60, 90, 120 })
    @DisplayName("las duraciones que ofrece el front pasan")
    void aceptaLasDuracionesReales(int minutos) {
        Set<ConstraintViolation<TurnoFijoRequest>> errores = validator.validate(abonoDe(minutos));
        assertTrue(errores.isEmpty(), "no debería rechazar un abono de " + minutos + " minutos: " + errores);
    }

    @ParameterizedTest
    @ValueSource(ints = { 6, 420 })
    @DisplayName("un número que no son minutos de turno se rechaza")
    void rechazaLoQueNoEsUnaDuracion(int minutos) {
        assertEquals(1, validator.validate(abonoDe(minutos)).size());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("sin duración es válido: el servicio usa la más corta que vende la cancha")
    void aceptaSinDuracion() {
        assertTrue(validator.validate(abonoDe(null)).isEmpty());
    }

}
