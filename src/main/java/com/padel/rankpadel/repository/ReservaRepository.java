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

    /**
     * Todos los turnos del día, de todas las canchas. Es lo que mira el mostrador: no le
     * sirve elegir una cancha, le sirve ver quién está jugando ahora y quién debe.
     */
    @Query("SELECT r FROM Reserva r LEFT JOIN FETCH r.pago LEFT JOIN FETCH r.cancha c "
            + "WHERE r.fecha = :fecha AND r.estado IN :estados ORDER BY r.horaInicio, c.nombre")
    List<Reserva> findDelDiaConCancha(@Param("fecha") LocalDate fecha,
            @Param("estados") Collection<EstadoReserva> estados);

    List<Reserva> findByEstadoAndExpiraEnBefore(EstadoReserva estado, LocalDateTime momento);

    List<Reserva> findByFechaAndEstadoIn(LocalDate fecha, Collection<EstadoReserva> estados);

    List<Reserva> findByFechaBetweenAndEstadoIn(LocalDate desde, LocalDate hasta, Collection<EstadoReserva> estados);

    /**
     * Turnos de un período con cancha, pago y ficha del cliente ya cargados. Las
     * estadísticas recorren medio año de reservas agrupando por cliente y por cancha: sin
     * el fetch, cada una de esas lecturas dispara su propia consulta.
     */
    @Query("SELECT r FROM Reserva r "
            + "LEFT JOIN FETCH r.cancha LEFT JOIN FETCH r.pago LEFT JOIN FETCH r.cliente "
            + "WHERE r.fecha BETWEEN :desde AND :hasta")
    List<Reserva> findParaEstadisticas(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /**
     * Candidatas a darse por jugadas. Solo filtra por fecha: si ya terminaron se decide
     * en Java, porque {@code horaFin} sola miente en los turnos que cruzan medianoche
     * (uno de 23 a 00 tiene horaFin 00:00, menor que cualquier hora del día).
     */
    List<Reserva> findByEstadoAndFechaLessThanEqual(EstadoReserva estado, LocalDate hasta);

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

    /**
     * Todos los turnos de un cliente sobre los que puede quedar saldo. La deuda de la
     * ficha se calcula sobre esto y no sobre el historial que se muestra: ese está
     * paginado, y un abonado pasa los 50 turnos en poco más de un año, así que lo impago
     * más viejo desaparecía del total sin ningún aviso.
     */
    @Query("SELECT r FROM Reserva r LEFT JOIN FETCH r.pago "
            + "WHERE r.cliente.id = :clienteId AND r.estado IN :estados")
    List<Reserva> findCobrablesDeCliente(@Param("clienteId") Long clienteId,
            @Param("estados") Collection<EstadoReserva> estados);

    /**
     * Turnos de la jornada: los de la fecha pedida más los del día anterior, porque un
     * turno de la madrugada pertenece a la sesión que arrancó el día antes. Sin esto el
     * panel mostraba libre una cancha que a la 1 AM estaba ocupada.
     */
    @Query("SELECT r FROM Reserva r LEFT JOIN FETCH r.cancha "
            + "WHERE r.fecha IN (:fecha, :vispera) AND r.estado IN :estados")
    List<Reserva> findDeLaJornada(@Param("fecha") LocalDate fecha, @Param("vispera") LocalDate vispera,
            @Param("estados") Collection<EstadoReserva> estados);

    /**
     * Con qué nombres reservó este teléfono. La ficha se crea con el nombre de la primera
     * reserva y no vuelve a tocarse, así que el club necesita ver si después el mismo
     * número pidió turno como "Grupo del jueves" o como otra persona.
     */
    @Query("""
        SELECT DISTINCT TRIM(r.clienteNombre) FROM Reserva r
        WHERE r.cliente.id = :clienteId AND r.clienteNombre IS NOT NULL
        """)
    List<String> nombresUsadosPor(@Param("clienteId") Long clienteId);

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
