package com.padel.rankpadel.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AprobarInscripcionRequest {

    private Long jugador1Id;
    private Long jugador2Id;
}
