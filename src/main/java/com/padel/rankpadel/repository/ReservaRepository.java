package com.padel.rankpadel.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByCanchaIdAndFecha(Long canchaId, LocalDate fecha);

    List<Reserva> findByEstadoAndExpiraEnBefore(EstadoReserva estado, LocalDateTime momento);

    List<Reserva> findByFechaAndEstadoIn(LocalDate fecha, Collection<EstadoReserva> estados);

    List<Reserva> findByFechaBetweenAndEstadoIn(LocalDate desde, LocalDate hasta, Collection<EstadoReserva> estados);

    @Query("SELECT r FROM Reserva r WHERE r.estado = :estado "
            + "AND (r.fecha < :hoy OR (r.fecha = :hoy AND r.horaFin <= :ahora))")
    List<Reserva> findConfirmadasFinalizadas(@Param("estado") EstadoReserva estado,
            @Param("hoy") LocalDate hoy, @Param("ahora") LocalTime ahora);

    long countByClienteTelefonoAndEstado(String clienteTelefono, EstadoReserva estado);

    long countByFechaAndEstado(LocalDate fecha, EstadoReserva estado);

    long countByEstado(EstadoReserva estado);

    List<Reserva> findByEstado(EstadoReserva estado);

    List<Reserva> findByFechaAndEstado(LocalDate fecha, EstadoReserva estado);

    List<Reserva> findByFechaBetweenAndEstado(LocalDate desde, LocalDate hasta, EstadoReserva estado);

    List<Reserva> findByPagoId(Long pagoId);
}
