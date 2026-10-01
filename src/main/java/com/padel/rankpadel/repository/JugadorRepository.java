package com.padel.rankpadel.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Jugador;
import java.util.List;
import com.padel.rankpadel.enums.Genero;
import com.padel.rankpadel.entity.Categoria;

public interface JugadorRepository extends JpaRepository<Jugador, Long> {

    List<Jugador> findByGenero(Genero genero);

    List<Jugador> findByActivoTrue();

    long countByActivoTrue();

    List<Jugador> findByCategoria(Categoria categoria);

    List<Jugador> findByCategoriaAndGenero(Categoria categoria, Genero genero);

    List<Jugador> findByNombreContainingIgnoreCase(String texto);

    List<Jugador> findByActivoTrueAndGeneroAndNombreNormalizado(Genero genero, String nombreNormalizado);

    boolean existsByActivoTrueAndNombreNormalizado(String nombreNormalizado);

    boolean existsByActivoTrueAndNombreNormalizadoAndIdNot(String nombreNormalizado, Long id);

    @Query("SELECT j FROM Jugador j LEFT JOIN FETCH j.categoria WHERE j.activo = true")
    List<Jugador> findAllConCategoria();

    @Query("SELECT j FROM Jugador j LEFT JOIN FETCH j.categoria WHERE j.activo = true AND j.nombreNormalizado LIKE CONCAT('%', :q, '%') ORDER BY j.nombre ASC, j.apellido ASC LIMIT 10")
    List<Jugador> buscarPorNombreNormalizado(@Param("q") String q);

    /**
     * Una página de jugadores con los filtros ya aplicados en la base.
     *
     * <p>Antes la pantalla se traía la tabla entera y filtraba en el navegador: con 200
     * jugadores no se nota, con 2.000 y un celular sí, y además el listado completo salía
     * por un endpoint público. La búsqueda replica la del front —empieza por nombre, por
     * apellido o por el nombre completo—, y como la colación del MySQL ignora acentos,
     * "peña" y "pena" encuentran lo mismo.
     *
     * <p>El {@code LEFT JOIN FETCH} sobre la categoría es a-uno, así que Hibernate pagina
     * en SQL: no cae en el paginado en memoria que sí provocaría una colección.
     */
    @Query(value = """
            SELECT j FROM Jugador j LEFT JOIN FETCH j.categoria c
            WHERE (:incluirBajas = true OR j.activo = true)
              AND (:genero IS NULL OR j.genero = :genero)
              AND (:categoriaId IS NULL OR c.id = :categoriaId)
              AND (:prefijo IS NULL
                   OR LOWER(CONCAT(j.nombre, ' ', j.apellido)) LIKE :prefijo
                   OR LOWER(j.nombre) LIKE :prefijo
                   OR LOWER(j.apellido) LIKE :prefijo)
            """,
            countQuery = """
            SELECT COUNT(j) FROM Jugador j
            WHERE (:incluirBajas = true OR j.activo = true)
              AND (:genero IS NULL OR j.genero = :genero)
              AND (:categoriaId IS NULL OR j.categoria.id = :categoriaId)
              AND (:prefijo IS NULL
                   OR LOWER(CONCAT(j.nombre, ' ', j.apellido)) LIKE :prefijo
                   OR LOWER(j.nombre) LIKE :prefijo
                   OR LOWER(j.apellido) LIKE :prefijo)
            """)
    Page<Jugador> buscarPagina(@Param("prefijo") String prefijo,
            @Param("genero") Genero genero,
            @Param("categoriaId") Long categoriaId,
            @Param("incluirBajas") boolean incluirBajas,
            Pageable pageable);

}
