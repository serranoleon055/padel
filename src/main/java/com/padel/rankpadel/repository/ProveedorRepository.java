package com.padel.rankpadel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.padel.rankpadel.entity.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    List<Proveedor> findByActivoTrueOrderByNombreAsc();

    List<Proveedor> findAllByOrderByActivoDescNombreAsc();
}
