package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.PromocionCancha;

public interface PromocionCanchaRepository extends JpaRepository<PromocionCancha, Long> {

    List<PromocionCancha> findByCanchaIdOrderByHoraDesdeAsc(Long canchaId);

    List<PromocionCancha> findByCanchaIdAndActivoTrue(Long canchaId);
}
