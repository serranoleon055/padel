package com.padel.rankpadel.mapper;

import org.springframework.stereotype.Component;

import com.padel.rankpadel.dto.request.CategoriaRequest;
import com.padel.rankpadel.dto.response.CategoriaResponse;
import com.padel.rankpadel.entity.Categoria;

@Component
public class CategoriaMapper {

    public Categoria requestToCategoria(CategoriaRequest categoriaRequest) {
        Categoria categoria = Categoria.builder()
                .nombre(categoriaRequest.getNombre())
                .nivel(categoriaRequest.getNivel())
                .edadMin(categoriaRequest.getEdadMin())
                .edadMax(categoriaRequest.getEdadMax())
                .genero(categoriaRequest.getGenero())
                .build();
        return categoria;
    }

    public CategoriaResponse categoriaToResponse(Categoria categoria) {

        CategoriaResponse categoriaDTO = CategoriaResponse.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .nivel(categoria.getNivel())
                .edadMin(categoria.getEdadMin())
                .edadMax(categoria.getEdadMax())
                .genero(categoria.getGenero())
                .build();

        return categoriaDTO;
    }

}
