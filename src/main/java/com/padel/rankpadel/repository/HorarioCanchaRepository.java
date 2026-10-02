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

    /**
     * Los horarios vigentes de un grupo de canchas, en una sola consulta.
     *
     * <p>Las estadísticas preguntaban el horario cancha por cancha y lo hacían tres veces
     * por request —la apertura del lugar, las horas abiertas del mes y la ocupación por
     * cancha—: con seis canchas eran dieciocho viajes a la base para leer siempre lo
     * mismo.
     */
    List<HorarioCancha> findByCanchaIdInAndActivoTrue(List<Long> canchaIds);
}
