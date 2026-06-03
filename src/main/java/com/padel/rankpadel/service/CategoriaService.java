package com.padel.rankpadel.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.CategoriaRequest;
import com.padel.rankpadel.dto.response.CategoriaResponse;
import com.padel.rankpadel.entity.Categoria;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.mapper.CategoriaMapper;
import com.padel.rankpadel.repository.CategoriaRepository;
import com.padel.rankpadel.repository.JugadorRepository;
import com.padel.rankpadel.repository.TorneoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;
    private final JugadorRepository jugadorRepository;
    private final TorneoRepository torneoRepository;

    public List<CategoriaResponse> listarTodos() {
        return categoriaRepository.findAll()
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
        categoria = categoriaMapper.requestToCategoria(categoriaRequest);
        categoria.setId(id);
        categoriaRepository.save(categoria);
        return categoriaMapper.categoriaToResponse(categoria);
    }

    @Transactional
    public void eliminar(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", id));

        long jugadoresAsociados = jugadorRepository.findByCategoria(categoria).size();
        if (jugadoresAsociados > 0) {
            throw new EstadoInvalidoException(
                    "No se puede eliminar la categoría \"" + categoria.getNombre() +
                            "\" porque tiene " + jugadoresAsociados + " jugador(es) asignado(s). " +
                            "Primero reasigná o eliminá esos jugadores.");
        }

        long torneosAsociados = torneoRepository.findAll().stream()
                .filter(t -> t.getCategorias().contains(categoria))
                .count();
        if (torneosAsociados > 0) {
            throw new EstadoInvalidoException(
                    "No se puede eliminar la categoría \"" + categoria.getNombre() +
                            "\" porque está asociada a " + torneosAsociados + " torneo(s). " +
                            "Primero quitá la categoría de esos torneos.");
        }

        categoriaRepository.delete(categoria);
    }

    @Transactional
    public void eliminarBatch(List<Long> ids) {
        List<Categoria> categorias = categoriaRepository.findAllById(ids);
        categoriaRepository.deleteAll(categorias);
    }
}