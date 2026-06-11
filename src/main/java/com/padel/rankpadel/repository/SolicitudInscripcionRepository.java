package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.SolicitudInscripcion;
import com.padel.rankpadel.enums.EstadoSolicitud;

@Repository
public interface SolicitudInscripcionRepository extends JpaRepository<SolicitudInscripcion, Long> {

    List<SolicitudInscripcion> findByTorneoId(Long torneoId);

    List<SolicitudInscripcion> findByTorneoIdAndEstado(Long torneoId, EstadoSolicitud estado);
}
