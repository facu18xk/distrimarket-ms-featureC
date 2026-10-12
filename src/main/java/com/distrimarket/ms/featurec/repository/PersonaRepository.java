package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.Persona;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonaRepository extends BaseRepository<Persona> {

    boolean existsByCi(String ci);

    boolean existsByCiAndIdNot(String ci, Long id);

    boolean existsByRuc(String ruc);

    boolean existsByRucAndIdNot(String ruc, Long id);
}
