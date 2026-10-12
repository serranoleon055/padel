package com.padel.rankpadel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.AvisoEnviado;

public interface AvisoEnviadoRepository extends JpaRepository<AvisoEnviado, Long> {

    List<AvisoEnviado> findByTorneoId(Long torneoId);

    Optional<AvisoEnviado> findByTorneoIdAndJugadorIdAndTema(Long torneoId, Long jugadorId, String tema);
}
