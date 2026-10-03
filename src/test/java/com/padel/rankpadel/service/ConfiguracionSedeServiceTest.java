package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.rankpadel.dto.ConfiguracionSedeDto;
import com.padel.rankpadel.entity.ConfiguracionSede;
import com.padel.rankpadel.repository.ConfiguracionSedeRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfiguracionSedeService - lo que se guarda tiene que volver")
class ConfiguracionSedeServiceTest {

    @Mock
    private ConfiguracionSedeRepository configuracionSedeRepository;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private MapsEmbedResolver mapsEmbedResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConfiguracionSedeService servicio;

    @BeforeEach
    void setUp() {
        servicio = new ConfiguracionSedeService(configuracionSedeRepository, objectMapper,
                imageStorageService, mapsEmbedResolver);
        lenient().when(configuracionSedeRepository.findById(1L)).thenReturn(Optional.empty());
        lenient().when(configuracionSedeRepository.save(any(ConfiguracionSede.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(mapsEmbedResolver.resolver(any())).thenReturn(null);
    }

    /**
     * El caso que ya pasó dos veces en este módulo: un campo nuevo se guarda bien pero no
     * se agrega al mapeo de salida, así que la pantalla lo muestra vacío y el club cree
     * que no se guardó. No hay nada en el guardado que lo detecte.
     */
    @Test
    @DisplayName("Los números de la configuración vuelven en la respuesta")
    void actualizar_devuelveLoGuardado() {
        ConfiguracionSedeDto pedido = ConfiguracionSedeDto.builder()
                .email("info@club.com")
                .cancelacionHorasMinimas(6)
                .fondoFijo(new BigDecimal("20000"))
                .descuentoMaximoMostrador(15)
                .build();

        ConfiguracionSedeDto devuelto = servicio.actualizar(pedido);

        assertThat(devuelto.getEmail()).isEqualTo("info@club.com");
        assertThat(devuelto.getCancelacionHorasMinimas()).isEqualTo(6);
        assertThat(devuelto.getFondoFijo()).isEqualByComparingTo("20000");
        assertThat(devuelto.getDescuentoMaximoMostrador()).isEqualTo(15);
    }

    @Test
    @DisplayName("El tope de descuento se acota entre 0 y 100")
    void actualizar_acotaElTope() {
        // Un tope de 150% no significa nada y uno negativo prohibiría más que el cero,
        // que ya es "ninguno".
        assertThat(servicio.actualizar(ConfiguracionSedeDto.builder()
                .descuentoMaximoMostrador(150).build()).getDescuentoMaximoMostrador())
                .isEqualTo(100);
        assertThat(servicio.actualizar(ConfiguracionSedeDto.builder()
                .descuentoMaximoMostrador(-5).build()).getDescuentoMaximoMostrador())
                .isZero();
    }

    @Test
    @DisplayName("Sin configurar, el mostrador no puede bonificar nada")
    void obtener_sinConfigurar_topeCero() {
        when(configuracionSedeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(servicio.obtener().getDescuentoMaximoMostrador()).isZero();
    }
}
