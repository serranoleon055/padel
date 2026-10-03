package com.padel.rankpadel.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.padel.rankpadel.entity.Recuento;
import com.padel.rankpadel.enums.EstadoRecuento;

public interface RecuentoRepository extends JpaRepository<Recuento, Long> {

    /**
     * El conteo que está abierto, si hay uno.
     *
     * <p>Se permite **uno solo** a la vez. Dos planillas abiertas congelan cada una su
     * propio {@code stockSistema} y después aplican las dos diferencias sobre el mismo
     * stock: la segunda descuenta de nuevo lo que la primera ya corrigió.
     */
    Optional<Recuento> findFirstByEstadoOrderByIdDesc(EstadoRecuento estado);

    @Query("""
        SELECT r FROM Recuento r
        ORDER BY r.fecha DESC, r.id DESC
        """)
    Page<Recuento> buscar(Pageable pageable);
}
