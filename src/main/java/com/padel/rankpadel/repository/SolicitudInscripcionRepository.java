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

    @Query("SELECT COUNT(s) FROM SolicitudInscripcion s WHERE s.estado = :estado AND s.torneo.estado = :estadoTorneo")
    long contarPendientesEnTorneos(@Param("estado") EstadoSolicitud estado, @Param("estadoTorneo") EstadoTorneo estadoTorneo);

    @Query("SELECT s FROM SolicitudInscripcion s WHERE s.estado = :estado AND s.torneo.estado = :estadoTorneo")
    List<SolicitudInscripcion> findPendientesEnTorneos(@Param("estado") EstadoSolicitud estado, @Param("estadoTorneo") EstadoTorneo estadoTorneo);
}
