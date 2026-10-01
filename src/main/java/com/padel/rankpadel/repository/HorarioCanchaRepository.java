package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.HorarioCancha;

public interface HorarioCanchaRepository extends JpaRepository<HorarioCancha, Long> {

    List<HorarioCancha> findByCanchaIdAndActivoTrue(Long canchaId);

    /**
     * Los horarios vigentes de todas las canchas en una sola consulta.
     *
     * <p>Resolver la jornada actual preguntaba el horario cancha por cancha: seis canchas
     * eran seis viajes a la base en cada carga del panel y en cada listado del día. Contra
     * una base gestionada, con la latencia de red de por medio, eso se nota.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT h FROM HorarioCancha h JOIN h.cancha c WHERE h.activo = true AND c.activo = true")
    List<HorarioCancha> findVigentesDeCanchasActivas();

    List<HorarioCancha> findByCanchaId(Long canchaId);
}
