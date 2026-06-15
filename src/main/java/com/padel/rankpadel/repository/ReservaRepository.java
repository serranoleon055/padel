package com.padel.rankpadel.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByCanchaIdAndFecha(Long canchaId, LocalDate fecha);

    List<Reserva> findByEstadoAndExpiraEnBefore(EstadoReserva estado, LocalDateTime momento);

    long countByClienteTelefonoAndEstado(String clienteTelefono, EstadoReserva estado);
}
