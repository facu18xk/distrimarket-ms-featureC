package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.MedioPago;

public interface MedioPagoRepository extends BaseRepository<MedioPago> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
