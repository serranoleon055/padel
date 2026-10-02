package com.padel.rankpadel.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padel.rankpadel.dto.ConfiguracionSedeDto;
import com.padel.rankpadel.entity.ConfiguracionSede;
import com.padel.rankpadel.repository.ConfiguracionSedeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfiguracionSedeService {

    private static final Long ID = 1L;

    private final ConfiguracionSedeRepository configuracionSedeRepository;
    private final ObjectMapper objectMapper;
    private final ImageStorageService imageStorageService;
    private final MapsEmbedResolver mapsEmbedResolver;

    public String subirImagenGaleria(MultipartFile file) {
        return imageStorageService.guardarGaleriaSede(file);
    }

    @Transactional(readOnly = true)
    public ConfiguracionSedeDto obtener() {
        ConfiguracionSede configuracion = configuracionSedeRepository.findById(ID).orElseGet(ConfiguracionSede::new);
        return aDto(configuracion);
    }

    @Transactional
    public ConfiguracionSedeDto actualizar(ConfiguracionSedeDto dto) {
        ConfiguracionSede configuracion = configuracionSedeRepository.findById(ID).orElseGet(ConfiguracionSede::new);
        configuracion.setId(ID);
        configuracion.setEmail(dto.getEmail());
        configuracion.setTelefono(dto.getTelefono());
        configuracion.setWhatsapp(dto.getWhatsapp());
        configuracion.setInstagram(dto.getInstagram());
        configuracion.setFacebook(dto.getFacebook());
        configuracion.setDireccion(dto.getDireccion());
        configuracion.setMapsEmbedUrl(mapsEmbedResolver.resolver(dto.getMapsEmbedUrl()));
        if (dto.getCancelacionHorasMinimas() != null) {
            configuracion.setCancelacionHorasMinimas(Math.max(0, dto.getCancelacionHorasMinimas()));
        }
        configuracion.setFondoFijo(dto.getFondoFijo());
        configuracion.setHorariosJson(aJson(dto.getHorarios()));
        configuracion.setGaleriaJson(aJson(dto.getGaleria()));
        configuracion.setFormasPagoJson(aJson(dto.getFormasPago()));
        if (dto.getMercadoPagoAccessToken() != null && !dto.getMercadoPagoAccessToken().isBlank()) {
            configuracion.setMercadoPagoAccessToken(dto.getMercadoPagoAccessToken().trim());
        }
        configuracionSedeRepository.save(configuracion);
        return aDto(configuracion);
    }

    private ConfiguracionSedeDto aDto(ConfiguracionSede configuracion) {
        return ConfiguracionSedeDto.builder()
                .email(configuracion.getEmail())
                .telefono(configuracion.getTelefono())
                .whatsapp(configuracion.getWhatsapp())
                .instagram(configuracion.getInstagram())
                .facebook(configuracion.getFacebook())
                .direccion(configuracion.getDireccion())
                .mapsEmbedUrl(configuracion.getMapsEmbedUrl())
                .cancelacionHorasMinimas(horasMinimasCancelacion(configuracion))
                .fondoFijo(configuracion.getFondoFijo())
                .horarios(desdeJson(configuracion.getHorariosJson(), new TypeReference<List<ConfiguracionSedeDto.HorarioSede>>() {}))
                .galeria(desdeJson(configuracion.getGaleriaJson(), new TypeReference<List<ConfiguracionSedeDto.FotoSede>>() {}))
                .formasPago(desdeJson(configuracion.getFormasPagoJson(), new TypeReference<List<String>>() {}))
                .mercadoPagoConfigurado(configuracion.getMercadoPagoAccessToken() != null
                        && !configuracion.getMercadoPagoAccessToken().isBlank())
                .build();
    }

    /**
     * Cuántas horas antes del turno se corta la cancelación por internet. Lo usa el
     * endpoint público del jugador; la configuración es una sola fila, así que se lee de
     * la entidad y no del DTO.
     */
    @Transactional(readOnly = true)
    public int horasMinimasCancelacion() {
        return horasMinimasCancelacion(configuracionSedeRepository.findById(ID).orElseGet(ConfiguracionSede::new));
    }

    private int horasMinimasCancelacion(ConfiguracionSede configuracion) {
        // Una fila vieja, de antes de V56, puede tener el campo nulo: se toma el mismo
        // valor por defecto que la migración, y no cero, que apagaría la cancelación sin
        // que el club lo haya decidido.
        return configuracion.getCancelacionHorasMinimas() != null
                ? configuracion.getCancelacionHorasMinimas()
                : 12;
    }

    private String aJson(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la configuración de la sede", e);
        }
    }

    private <T> List<T> desdeJson(String json, TypeReference<List<T>> tipo) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
