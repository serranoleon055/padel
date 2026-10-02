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

import java.util.Optional;

import com.padel.rankpadel.entity.Reserva;
import com.padel.rankpadel.enums.EstadoReserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * El turno del enlace que le llegó al jugador. Trae cancha, sede y pago porque la
     * pantalla pública los muestra todos y no puede disparar una consulta por cada uno.
     */
    @Query("SELECT r FROM Reserva r LEFT JOIN FETCH r.cancha c LEFT JOIN FETCH c.lugar "
            + "LEFT JOIN FETCH r.pago WHERE r.tokenPublico = :token")
    Optional<Reserva> findByTokenPublico(@Param("token") String token);

    /**
     * Candidatos a recordatorio: confirmados, con mail, y a los que todavía no se les
     * avisó. El rango de fechas es amplio a propósito —la hora exacta la decide el
     * servicio, que sabe resolver la jornada— pero acota la consulta a un par de días.
     */
    @Query("""
        SELECT r FROM Reserva r
        JOIN FETCH r.cancha
        LEFT JOIN FETCH r.cliente
        WHERE r.estado = com.padel.rankpadel.enums.EstadoReserva.CONFIRMADA
          AND r.recordatorioEnviadoEn IS NULL
          AND r.fecha BETWEEN :desde AND :hasta
          AND (r.clienteEmail IS NOT NULL OR r.cliente.email IS NOT NULL)
        """)
    List<Reserva> findParaRecordar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

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
    @Query("""
            SELECT r FROM Reserva r
            LEFT JOIN FETCH r.cancha c
            LEFT JOIN FETCH r.pago
            LEFT JOIN FETCH r.cliente
            WHERE r.fecha BETWEEN :desde AND :hasta
              AND (:lugarId IS NULL OR c.lugar.id = :lugarId)
            """)
    List<Reserva> findParaEstadisticas(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
            @Param("lugarId") Long lugarId);

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

    // ── Seguimiento de clientes ────────────────────────────────────────────────────
    // El club ya tiene todo el historial cargado; lo que le faltaba era que el sistema le
    // dijera a quién conviene escribirle. Las tres consultas agrupan en la base: recorrer
    // los clientes en Java para contar sus turnos sería un N+1 con nombre de informe.

    /**
     * Los que venían y dejaron de venir. Ordenados por el turno más reciente primero: el
     * que se enfrió hace tres semanas se recupera con un mensaje; el de hace dos años, no.
     */
    @Query("""
        SELECT c.id AS clienteId, c.nombre AS nombre, c.telefono AS telefono,
               COUNT(r) AS turnos,
               SUM(COALESCE(r.precioAplicado, 0)) AS gastado,
               MAX(r.fecha) AS ultimoTurno, MIN(r.fecha) AS primerTurno
        FROM Reserva r JOIN r.cliente c
        WHERE r.estado IN :estados
        GROUP BY c.id, c.nombre, c.telefono
        HAVING MAX(r.fecha) < :corte AND COUNT(r) >= :turnosMinimos
        ORDER BY MAX(r.fecha) DESC
        """)
    List<ClienteSeguimiento> clientesDormidos(@Param("corte") LocalDate corte,
            @Param("turnosMinimos") long turnosMinimos,
            @Param("estados") Collection<EstadoReserva> estados,
            Pageable pageable);

    /**
     * Los que más dejaron en el club en el período. Los que hay que cuidar.
     *
     * <p>Solo cuenta turnos que ya se jugaron: un abonado tiene seis semanas de turnos
     * confirmados por adelantado, y sumarlos lo pondría primero en la lista con plata que
     * todavía no entró. Es el mismo criterio que usa "Lo que más dejan" en Estadísticas.
     */
    @Query("""
        SELECT c.id AS clienteId, c.nombre AS nombre, c.telefono AS telefono,
               COUNT(r) AS turnos,
               SUM(COALESCE(r.precioAplicado, 0)) AS gastado,
               MAX(r.fecha) AS ultimoTurno, MIN(r.fecha) AS primerTurno
        FROM Reserva r JOIN r.cliente c
        WHERE r.estado IN :estados AND r.fecha BETWEEN :desde AND :hoy
        GROUP BY c.id, c.nombre, c.telefono
        ORDER BY SUM(COALESCE(r.precioAplicado, 0)) DESC
        """)
    List<ClienteSeguimiento> mejoresClientes(@Param("desde") LocalDate desde,
            @Param("hoy") LocalDate hoy,
            @Param("estados") Collection<EstadoReserva> estados,
            Pageable pageable);

    /**
     * Los que jugaron por primera vez hace poco: el momento de que vuelvan una segunda.
     *
     * <p>Igual que arriba, mira hasta hoy: con los turnos futuros adentro, "último turno"
     * mostraba una fecha que todavía no pasó y los días sin venir salían en negativo.
     */
    @Query("""
        SELECT c.id AS clienteId, c.nombre AS nombre, c.telefono AS telefono,
               COUNT(r) AS turnos,
               SUM(COALESCE(r.precioAplicado, 0)) AS gastado,
               MAX(r.fecha) AS ultimoTurno, MIN(r.fecha) AS primerTurno
        FROM Reserva r JOIN r.cliente c
        WHERE r.estado IN :estados AND r.fecha <= :hoy
        GROUP BY c.id, c.nombre, c.telefono
        HAVING MIN(r.fecha) >= :desde
        ORDER BY MIN(r.fecha) DESC
        """)
    List<ClienteSeguimiento> clientesNuevos(@Param("desde") LocalDate desde,
            @Param("hoy") LocalDate hoy,
            @Param("estados") Collection<EstadoReserva> estados,
            Pageable pageable);

    interface ClienteSeguimiento {
        Long getClienteId();

        String getNombre();

        String getTelefono();

        long getTurnos();

        BigDecimal getGastado();

        LocalDate getUltimoTurno();

        LocalDate getPrimerTurno();
    }

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
