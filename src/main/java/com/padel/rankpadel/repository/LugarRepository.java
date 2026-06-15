package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Lugar;

public interface LugarRepository extends JpaRepository<Lugar, Long> {

    List<Lugar> findByArchivadoFalse();

}
