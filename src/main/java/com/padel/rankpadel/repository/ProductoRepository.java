package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Catálogo del mostrador. El proveedor viene cargado porque el listado lo muestra y
     * son pocas filas: un club no tiene cientos de productos.
     */
    @Query("""
        SELECT p FROM Producto p
        LEFT JOIN FETCH p.proveedor
        WHERE (:soloActivos = false OR p.activo = true)
          AND (:busqueda IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')))
        ORDER BY p.categoria, p.nombre
        """)
    List<Producto> buscar(@Param("busqueda") String busqueda, @Param("soloActivos") boolean soloActivos);

    /** Lo que hay que reponer, para avisarlo en el panel sin que nadie tenga que mirar. */
    @Query("""
        SELECT p FROM Producto p
        WHERE p.activo = true AND p.controlaStock = true
          AND p.stockMinimo > 0 AND p.stock <= p.stockMinimo
        ORDER BY p.stock, p.nombre
        """)
    List<Producto> conStockBajo();

    long countByProveedorIdAndActivoTrue(Long proveedorId);

    /** Para chequear nombre repetido sin importar mayúsculas ni espacios de más. */
    java.util.Optional<Producto> findByNombreIgnoreCase(String nombre);
}
