package com.padel.rankpadel.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.CategoriaRequest;
import com.padel.rankpadel.dto.response.CategoriaResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.CategoriaMapper;
import com.padel.rankpadel.repository.CategoriaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    public List<CategoriaResponse> listarTodos() {
        return categoriaRepository.findByArchivadoFalse()
                .stream()
                .map(categoriaMapper::categoriaToResponse)
                .collect(Collectors.toList());
    }

    public CategoriaResponse buscarPorId(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", id));
        return categoriaMapper.categoriaToResponse(categoria);
    }

    @Transactional
    public CategoriaResponse crear(CategoriaRequest categoriaRequest) {
        Categoria categoria = categoriaMapper.requestToCategoria(categoriaRequest);
        categoriaRepository.save(categoria);
        return categoriaMapper.categoriaToResponse(categoria);
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaRequest categoriaRequest) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", id));
        categoria.setNombre(categoriaRequest.getNombre());
        categoria.setNivel(categoriaRequest.getNivel());
        categoria.setEdadMin(categoriaRequest.getEdadMin());
        categoria.setEdadMax(categoriaRequest.getEdadMax());
        categoria.setGenero(categoriaRequest.getGenero());
        categoriaRepository.save(categoria);
        return categoriaMapper.categoriaToResponse(categoria);
    }

    @Transactional
    public void eliminar(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", id));
        categoria.setArchivado(true);
        categoriaRepository.save(categoria);
    }

    @Transactional
    public void eliminarBatch(List<Long> ids) {
        List<Categoria> categorias = categoriaRepository.findAllById(ids);
        categorias.forEach(categoria -> categoria.setArchivado(true));
        categoriaRepository.saveAll(categorias);
    }
}
