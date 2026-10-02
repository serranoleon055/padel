package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.PresentacionProducto;

public interface PresentacionProductoRepository extends JpaRepository<PresentacionProducto, Long> {

    List<PresentacionProducto> findByProductoIdAndActivoTrueOrderByOrdenAscIdAsc(Long productoId);

    List<PresentacionProducto> findByProductoIdOrderByOrdenAscIdAsc(Long productoId);

    /** Las de varios productos de una vez, para no pedirlas fila por fila en el listado. */
    List<PresentacionProducto> findByProductoIdInAndActivoTrueOrderByOrdenAscIdAsc(List<Long> productoIds);

    java.util.Optional<PresentacionProducto> findByProductoIdAndNombreIgnoreCase(Long productoId, String nombre);
}
