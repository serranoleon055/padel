package com.padel.rankpadel.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.PresentacionRequest;
import com.padel.rankpadel.dto.response.PresentacionResponse;
import com.padel.rankpadel.entity.PresentacionProducto;
import com.padel.rankpadel.entity.Producto;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.PresentacionProductoRepository;
import com.padel.rankpadel.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Las formas de vender un producto: la pelota suelta y el tubo de tres.
 *
 * <p>Un producto sin presentaciones se vende como siempre, por su precio y de a una. Las
 * presentaciones son opcionales y existen para el caso real del club: el stock se lleva en
 * pelotas y el mostrador vende las dos cosas, con el tubo más barato que tres sueltas.
 */
@Service
@RequiredArgsConstructor
public class PresentacionProductoService {

    private final PresentacionProductoRepository presentacionProductoRepository;
    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public List<PresentacionResponse> listar(Long productoId, boolean incluirBajas) {
        List<PresentacionProducto> presentaciones = incluirBajas
                ? presentacionProductoRepository.findByProductoIdOrderByOrdenAscIdAsc(productoId)
                : presentacionProductoRepository.findByProductoIdAndActivoTrueOrderByOrdenAscIdAsc(productoId);
        return presentaciones.stream().map(this::aResponse).toList();
    }

    @Transactional
    public PresentacionResponse crear(Long productoId, PresentacionRequest request) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productoId));
        exigirControlDeStock(producto);
        exigirNombreLibre(productoId, request.getNombre(), null);

        PresentacionProducto presentacion = presentacionProductoRepository.save(PresentacionProducto.builder()
                .producto(producto)
                .nombre(request.getNombre().trim())
                .unidades(request.getUnidades())
                .precioVenta(request.getPrecioVenta())
                .orden(request.getOrden() != null ? request.getOrden() : 0)
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build());
        return aResponse(presentacion);
    }

    @Transactional
    public PresentacionResponse actualizar(Long id, PresentacionRequest request) {
        PresentacionProducto presentacion = buscar(id);
        exigirNombreLibre(presentacion.getProducto().getId(), request.getNombre(), id);
        // Cambiar cuántas unidades trae NO reescribe lo ya vendido: `VentaItem.factor`
        // está congelado desde V62, igual que el precio y el costo.
        presentacion.setNombre(request.getNombre().trim());
        presentacion.setUnidades(request.getUnidades());
        presentacion.setPrecioVenta(request.getPrecioVenta());
        if (request.getOrden() != null) {
            presentacion.setOrden(request.getOrden());
        }
        presentacionProductoRepository.save(presentacion);
        return aResponse(presentacion);
    }

    /** Baja lógica: las ventas viejas siguen apuntando a ella y tienen que poder leerse. */
    @Transactional
    public void darDeBaja(Long id) {
        PresentacionProducto presentacion = buscar(id);
        presentacion.setActivo(false);
        presentacionProductoRepository.save(presentacion);
    }

    @Transactional
    public PresentacionResponse reactivar(Long id) {
        PresentacionProducto presentacion = buscar(id);
        presentacion.setActivo(true);
        presentacionProductoRepository.save(presentacion);
        return aResponse(presentacion);
    }

    private PresentacionProducto buscar(Long id) {
        return presentacionProductoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Presentación", id));
    }

    /**
     * Un producto que no lleva control de stock (el alquiler de paleta, el café) no tiene
     * unidades que agrupar: un "pack de 3 cafés" no saca nada del depósito y lo único que
     * haría es confundir el listado del mostrador. Si el club quiere cobrarlo distinto,
     * eso es otro producto.
     */
    private void exigirControlDeStock(Producto producto) {
        if (!producto.isControlaStock()) {
            throw new EstadoInvalidoException("\"" + producto.getNombre()
                    + "\" no lleva control de stock, así que no tiene sentido venderlo por packs.");
        }
    }

    private void exigirNombreLibre(Long productoId, String nombre, Long idAExcluir) {
        presentacionProductoRepository.findByProductoIdAndNombreIgnoreCase(productoId, nombre.trim())
                .filter(existente -> !existente.getId().equals(idAExcluir))
                .ifPresent(existente -> {
                    throw new EstadoInvalidoException(
                            "Ese producto ya tiene una presentación llamada \"" + existente.getNombre() + "\".");
                });
    }

    PresentacionResponse aResponse(PresentacionProducto presentacion) {
        BigDecimal costoBase = presentacion.getProducto() != null
                ? presentacion.getProducto().costoDeValuacion()
                : null;
        BigDecimal margen = costoBase != null && presentacion.getPrecioVenta() != null
                ? presentacion.getPrecioVenta()
                        .subtract(costoBase.multiply(BigDecimal.valueOf(presentacion.getUnidades())))
                : null;
        return PresentacionResponse.builder()
                .id(presentacion.getId())
                .nombre(presentacion.getNombre())
                .unidades(presentacion.getUnidades())
                .precioVenta(presentacion.getPrecioVenta())
                .margenUnitario(margen)
                .orden(presentacion.getOrden())
                .activo(presentacion.isActivo())
                .build();
    }
}
