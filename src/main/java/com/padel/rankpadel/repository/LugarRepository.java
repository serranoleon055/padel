package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.padel.rankpadel.entity.Lugar;

@Repository
public interface LugarRepository extends JpaRepository<Lugar, Long> {

    List<Lugar> findByArchivadoFalse();

}
