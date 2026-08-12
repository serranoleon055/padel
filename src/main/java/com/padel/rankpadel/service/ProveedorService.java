package com.padel.rankpadel.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.ProveedorRequest;
import com.padel.rankpadel.dto.response.ProveedorResponse;
import com.padel.rankpadel.entity.Proveedor;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.ProductoRepository;
import com.padel.rankpadel.repository.ProveedorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listar(boolean soloActivos) {
        List<Proveedor> proveedores = soloActivos
                ? proveedorRepository.findByActivoTrueOrderByNombreAsc()
                : proveedorRepository.findAllByOrderByActivoDescNombreAsc();
        return proveedores.stream().map(this::aResponse).toList();
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        Proveedor proveedor = Proveedor.builder()
                .nombre(request.getNombre().trim())
                .telefono(request.getTelefono())
                .notas(request.getNotas())
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build();
        proveedorRepository.save(proveedor);
        return aResponse(proveedor);
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest request) {
        Proveedor proveedor = buscar(id);
        proveedor.setNombre(request.getNombre().trim());
        proveedor.setTelefono(request.getTelefono());
        proveedor.setNotas(request.getNotas());
        if (request.getActivo() != null) {
            proveedor.setActivo(request.getActivo());
        }
        proveedorRepository.save(proveedor);
        return aResponse(proveedor);
    }

    /**
     * Baja lógica: el proveedor queda enlazado a los productos que le compramos y a su
     * historial de compras, así que no se borra.
     */
    @Transactional
    public void darDeBaja(Long id) {
        Proveedor proveedor = buscar(id);
        proveedor.setActivo(false);
        proveedorRepository.save(proveedor);
    }

    private Proveedor buscar(Long id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
    }

    private ProveedorResponse aResponse(Proveedor proveedor) {
        return ProveedorResponse.builder()
                .id(proveedor.getId())
                .nombre(proveedor.getNombre())
                .telefono(proveedor.getTelefono())
                .notas(proveedor.getNotas())
                .activo(proveedor.isActivo())
                .productos(productoRepository.countByProveedorIdAndActivoTrue(proveedor.getId()))
                .build();
    }
}
