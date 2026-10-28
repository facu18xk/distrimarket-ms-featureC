package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.CampaniaEnvio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaniaEnvioRepository extends JpaRepository<CampaniaEnvio, Long> {

    long countByIdCampaniaAndEnviadoTrue(Long idCampania);
}
