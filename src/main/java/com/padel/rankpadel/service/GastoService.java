package com.padel.rankpadel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.GastoRequest;
import com.padel.rankpadel.dto.response.GastoResponse;
import com.padel.rankpadel.entity.Gasto;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.GastoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GastoService {

    private static final Logger log = LoggerFactory.getLogger(GastoService.class);

    private final GastoRepository gastoRepository;

    @Transactional
    public GastoResponse registrar(GastoRequest request) {
        Gasto gasto = gastoRepository.save(Gasto.builder()
                .fecha(request.getFecha())
                .categoria(request.getCategoria())
                .descripcion(request.getDescripcion().trim())
                .monto(request.getMonto())
                .medio(request.getMedio())
                .proveedor(request.getProveedor() != null ? request.getProveedor().trim() : null)
                .registradoPor(usuarioActual())
                .notas(request.getNotas())
                .creadoEn(LocalDateTime.now())
                .build());
        return aResponse(gasto);
    }

    @Transactional
    public GastoResponse actualizar(Long id, GastoRequest request) {
        Gasto gasto = buscar(id);
        gasto.setFecha(request.getFecha());
        gasto.setCategoria(request.getCategoria());
        gasto.setDescripcion(request.getDescripcion().trim());
        gasto.setMonto(request.getMonto());
        gasto.setMedio(request.getMedio());
        gasto.setProveedor(request.getProveedor() != null ? request.getProveedor().trim() : null);
        gasto.setNotas(request.getNotas());
        gastoRepository.save(gasto);
        return aResponse(gasto);
    }

    /** Borrar un egreso cambia la rentabilidad del mes: queda registrado quién lo hizo. */
    @Transactional
    public void eliminar(Long id) {
        Gasto gasto = buscar(id);
        log.info("[caja] {} eliminó el gasto {} de ${} ({} - {})",
                usuarioActual(), id, gasto.getMonto(), gasto.getCategoria(), gasto.getDescripcion());
        gastoRepository.delete(gasto);
    }

    @Transactional(readOnly = true)
    public List<GastoResponse> listarDelDia(LocalDate fecha) {
        return gastoRepository.findByFechaOrderByIdAsc(fecha).stream().map(this::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GastoResponse> listarEntre(LocalDate desde, LocalDate hasta) {
        return gastoRepository.findByFechaBetweenOrderByFechaDesc(desde, hasta).stream()
                .map(this::aResponse)
                .toList();
    }

    private Gasto buscar(Long id) {
        return gastoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto", id));
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private GastoResponse aResponse(Gasto gasto) {
        return GastoResponse.builder()
                .id(gasto.getId())
                .fecha(gasto.getFecha())
                .categoria(gasto.getCategoria() != null ? gasto.getCategoria().name() : null)
                .descripcion(gasto.getDescripcion())
                .monto(gasto.getMonto())
                .medio(gasto.getMedio() != null ? gasto.getMedio().name() : null)
                .proveedor(gasto.getProveedor())
                .registradoPor(gasto.getRegistradoPor())
                .notas(gasto.getNotas())
                .build();
    }
}
