package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.ConfiguracionPuntos;

@Repository
public interface ConfiguracionPuntosRepository extends JpaRepository<ConfiguracionPuntos, Long> {
    Optional<ConfiguracionPuntos> findByTorneoIdAndNombreRonda(Long torneoId, String nombreRonda);

    List<ConfiguracionPuntos> findByTorneoIdOrderByOrden(Long torneoId);
}
