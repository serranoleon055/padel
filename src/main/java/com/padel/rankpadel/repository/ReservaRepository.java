package com.padel.rankpadel.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    // El pago viene en el mismo viaje: el listado del día muestra estado de seña y saldo
    // de cada turno, y sin el fetch eso era una consulta por fila.
    @Query("SELECT r FROM Reserva r LEFT JOIN FETCH r.pago LEFT JOIN FETCH r.cancha "
            + "WHERE r.cancha.id = :canchaId AND r.fecha = :fecha")
    List<Reserva> findByCanchaIdAndFecha(@Param("canchaId") Long canchaId, @Param("fecha") LocalDate fecha);

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

    /**
     * Fechas ya generadas para un turno fijo, en cualquier estado. Se consultan en
     * cualquier estado a propósito: si el club dio de baja la reserva de una semana
     * puntual, el generador no debe volver a crearla.
     */
    @Query("SELECT r.fecha FROM Reserva r WHERE r.turnoFijo.id = :turnoFijoId AND r.fecha >= :desde")
    List<LocalDate> findFechasGeneradas(@Param("turnoFijoId") Long turnoFijoId, @Param("desde") LocalDate desde);

    List<Reserva> findByTurnoFijoIdAndFechaGreaterThanEqual(Long turnoFijoId, LocalDate desde);

    @Query("""
        SELECT r FROM Reserva r
        LEFT JOIN FETCH r.cancha
        WHERE r.cliente.id = :clienteId
        ORDER BY r.fecha DESC, r.horaInicio DESC
        """)
    List<Reserva> findHistorialCliente(@Param("clienteId") Long clienteId, Pageable pageable);

    /**
     * Resumen por cliente en una sola consulta: sin esto, un listado de 50 clientes
     * dispararía 50 consultas de estadísticas.
     */
    @Query("""
        SELECT r.cliente.id AS clienteId,
               COUNT(r) AS totales,
               SUM(CASE WHEN r.estado IN (com.padel.rankpadel.enums.EstadoReserva.CONFIRMADA,
                                          com.padel.rankpadel.enums.EstadoReserva.FINALIZADA)
                        THEN 1 ELSE 0 END) AS jugados,
               SUM(CASE WHEN r.estado IN (com.padel.rankpadel.enums.EstadoReserva.CANCELADA,
                                          com.padel.rankpadel.enums.EstadoReserva.RECHAZADA,
                                          com.padel.rankpadel.enums.EstadoReserva.EXPIRADA)
                        THEN 1 ELSE 0 END) AS caidos,
               SUM(CASE WHEN r.estado = com.padel.rankpadel.enums.EstadoReserva.NO_SHOW
                        THEN 1 ELSE 0 END) AS noShows,
               SUM(CASE WHEN r.estado IN (com.padel.rankpadel.enums.EstadoReserva.CONFIRMADA,
                                          com.padel.rankpadel.enums.EstadoReserva.FINALIZADA)
                        THEN COALESCE(r.precioAplicado, 0) ELSE 0 END) AS gastado,
               MAX(r.fecha) AS ultimoTurno
        FROM Reserva r
        WHERE r.cliente.id IN :clienteIds
        GROUP BY r.cliente.id
        """)
    List<ResumenCliente> resumenPorCliente(@Param("clienteIds") List<Long> clienteIds);

    interface ResumenCliente {
        Long getClienteId();

        long getTotales();

        long getJugados();

        long getCaidos();

        long getNoShows();

        BigDecimal getGastado();

        LocalDate getUltimoTurno();
    }
}
