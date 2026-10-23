package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.Timbrado;

public interface TimbradoRepository extends BaseRepository<Timbrado> {

    boolean existsByNumeroTimbrado(String numeroTimbrado);

    boolean existsByNumeroTimbradoAndIdNot(String numeroTimbrado, Long id);
}
