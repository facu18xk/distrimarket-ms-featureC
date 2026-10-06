package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface ClienteRepository extends BaseRepository<Cliente> {


    @Override
    @EntityGraph(attributePaths = "persona")
    Page<Cliente> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "persona")
    Optional<Cliente> findById(Long id);
}
