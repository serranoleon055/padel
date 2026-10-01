package com.padel.rankpadel.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Pago;
import com.padel.rankpadel.enums.EstadoPago;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findByReferenciaExterna(String referenciaExterna);

    List<Pago> findByEstadoAndCreadoEnAfter(EstadoPago estado, LocalDateTime desde);

    List<Pago> findByEstadoAndPagadoEnAfter(EstadoPago estado, LocalDateTime desde);

    /**
     * Las señas acreditadas en una jornada del club. Por jornada y no por día de
     * calendario, para que el arqueo de la noche cuadre con lo que cobró el mostrador
     * (ver V55).
     */
    List<Pago> findByEstadoAndJornada(EstadoPago estado, java.time.LocalDate jornada);
}
