package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.enums.EstadoSolicitud;
import com.padel.rankpadel.enums.EstadoTorneo;

public interface SolicitudInscripcionRepository extends JpaRepository<SolicitudInscripcion, Long> {

    List<SolicitudInscripcion> findByTorneoId(Long torneoId);

    List<SolicitudInscripcion> findByTorneoIdAndEstado(Long torneoId, EstadoSolicitud estado);

    SolicitudInscripcion findByPagoId(Long pagoId);

    long countByTorneoIdAndCategoriaIdAndPagadaTrueAndEstado(Long torneoId, Long categoriaId, EstadoSolicitud estado);

    long countByEstado(EstadoSolicitud estado);

    long countByTelefonoContactoAndEstado(String telefonoContacto, EstadoSolicitud estado);

    @Query("SELECT COUNT(s) FROM SolicitudInscripcion s WHERE s.estado = :estado AND s.torneo.estado = :estadoTorneo")
    long contarPendientesEnTorneos(@Param("estado") EstadoSolicitud estado, @Param("estadoTorneo") EstadoTorneo estadoTorneo);

    @Query("SELECT s FROM SolicitudInscripcion s WHERE s.estado = :estado AND s.torneo.estado = :estadoTorneo")
    List<SolicitudInscripcion> findPendientesEnTorneos(@Param("estado") EstadoSolicitud estado, @Param("estadoTorneo") EstadoTorneo estadoTorneo);

    /**
     * Las inscripciones aprobadas de la ventana que mira el panel.
     *
     * <p>Antes el panel hacía {@code findAll()} y filtraba en Java: se traía el historial
     * completo de inscripciones del club —que crece para siempre— y encima disparaba una
     * consulta por fila al leer {@code solicitud.getTorneo()}, que es LAZY. Era el N+1 más
     * caro del servicio y el único sin techo. Acá el filtro de fecha, de estado y de sede
     * van en el WHERE, y el torneo y la categoría vienen en el mismo viaje.
     */
    @Query("""
            SELECT s FROM SolicitudInscripcion s
            JOIN FETCH s.torneo t
            LEFT JOIN FETCH s.categoria
            WHERE s.estado = :estado
              AND s.creadoEn >= :desde
              AND (:lugarId IS NULL OR t.lugar.id = :lugarId)
            """)
    List<SolicitudInscripcion> findAprobadasDesde(@Param("estado") EstadoSolicitud estado,
            @Param("desde") java.time.LocalDateTime desde, @Param("lugarId") Long lugarId);

    /**
     * Lo que facturó cada torneo en inscripciones, agrupado en la base.
     *
     * <p>El embudo mira los torneos ABIERTOS, que pueden ser más viejos que la ventana del
     * panel, así que no alcanza con la consulta de arriba. Pero tampoco hace falta traer
     * las filas: lo único que se necesita es la suma.
     *
     * <p>Usa el costo congelado en la solicitud y cae al del torneo solo para las viejas,
     * de antes de que existiera la columna (V60). El total es POR JUGADOR: la cantidad de
     * integrantes la multiplica el servicio.
     */
    @Query("""
            SELECT t.id AS torneoId,
                   COALESCE(SUM(COALESCE(s.costoAplicado, t.costoInscripcionJugador)), 0) AS totalPorJugador
            FROM SolicitudInscripcion s JOIN s.torneo t
            WHERE s.estado = :estado AND t.id IN :torneoIds
            GROUP BY t.id
            """)
    List<IngresoPorTorneo> sumarAprobadasPorTorneo(@Param("estado") EstadoSolicitud estado,
            @Param("torneoIds") List<Long> torneoIds);

    interface IngresoPorTorneo {
        Long getTorneoId();

        java.math.BigDecimal getTotalPorJugador();
    }
}
