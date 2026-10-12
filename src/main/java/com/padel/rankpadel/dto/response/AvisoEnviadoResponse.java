package com.padel.rankpadel.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvisoEnviadoResponse {
    private Long jugadorId;
    private String tema;
    private String clave;
    private LocalDateTime enviadoEn;
    private String enviadoPor;
}
