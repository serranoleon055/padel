package com.padel.rankpadel.dto.response;

import java.util.List;

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
public class PagedResponse<T> {

    private List<T> contenido;
    private int pagina;
    private int tamanio;
    private long totalElementos;
    private int totalPaginas;
    private boolean esPrimera;
    private boolean esUltima;

    public static <T> PagedResponse<T> of(List<T> elementos, int pagina, int tamanio) {
        int total = elementos.size();
        int totalPaginas = tamanio > 0 ? (int) Math.ceil((double) total / tamanio) : 1;
        int tamanioSeguro = tamanio > 0 ? tamanio : total;
        int desde = Math.min(pagina * tamanioSeguro, total);
        int hasta = Math.min(desde + tamanioSeguro, total);
        List<T> contenido = elementos.subList(desde, hasta);

        return PagedResponse.<T>builder()
                .contenido(contenido)
                .pagina(pagina)
                .tamanio(tamanioSeguro)
                .totalElementos(total)
                .totalPaginas(totalPaginas)
                .esPrimera(pagina == 0)
                .esUltima(pagina >= totalPaginas - 1)
                .build();
    }

}
