package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByTelefonoNormalizado(String telefonoNormalizado);

    @Query("""
        SELECT c FROM Cliente c
        WHERE :busqueda IS NULL OR :busqueda = ''
           OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
           OR c.telefonoNormalizado LIKE CONCAT('%', :busqueda, '%')
        """)
    Page<Cliente> buscar(@Param("busqueda") String busqueda, Pageable pageable);

    @Query("SELECT j.cliente.id FROM Jugador j WHERE j.cliente.id IN :ids")
    List<Long> idsConJugador(@Param("ids") List<Long> ids);
}
