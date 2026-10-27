package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface ClienteRepository extends BaseRepository<Cliente> {

    /**
     * @EntityGraph precarga la relación @ManyToOne LAZY "persona" mediante LEFT JOIN
     * en la misma consulta paginada, evitando N consultas adicionales a la tabla personas.
     */
    @Override
    @EntityGraph(attributePaths = "persona")
    Page<Cliente> findAll(Pageable pageable);

    /**
     * @EntityGraph asegura que toda búsqueda paginada con filtros (Specification) traiga
     * la entidad "persona" en el mismo SELECT principal, evitando consultas N+1 al mapear a DTO.
     */
    @Override
    @EntityGraph(attributePaths = "persona")
    Page<Cliente> findAll(Specification<Cliente> spec, Pageable pageable);

    /**
     * @EntityGraph precarga los datos de "persona" al buscar un cliente por ID en una sola consulta SQL.
     */
    @Override
    @EntityGraph(attributePaths = "persona")
    Optional<Cliente> findById(Long id);
}
