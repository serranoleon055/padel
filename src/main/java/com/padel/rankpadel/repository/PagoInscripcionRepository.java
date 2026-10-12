package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.PagoInscripcion;

public interface PagoInscripcionRepository extends JpaRepository<PagoInscripcion, Long> {

    List<PagoInscripcion> findByParejaTorneoIdOrderByFechaAscIdAsc(Long torneoId);

    List<PagoInscripcion> findByParejaId(Long parejaId);
}
