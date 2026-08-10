package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.TarifaCancha;

public interface TarifaCanchaRepository extends JpaRepository<TarifaCancha, Long> {

    List<TarifaCancha> findByCanchaIdOrderByHoraDesdeAsc(Long canchaId);

    List<TarifaCancha> findByCanchaIdAndActivoTrue(Long canchaId);
}
