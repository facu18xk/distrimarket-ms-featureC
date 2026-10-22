package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.BaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface BaseService<E extends BaseEntity, REQUEST_DTO, RESPONSE_DTO> {

    Page<E> list(Pageable pageable);

    Page<E> search(Specification<E> specification, Pageable pageable);

    E get(Long id);

    default E getForRead(Long id) {
        return get(id);
    }

    E create(E entity);

    E update(Long id, E entity);

    void delete(Long id);

    // Operaciones que usa la API: trabajan con DTO, que es lo que se guarda en caché.

    RESPONSE_DTO obtener(Long id);

    RESPONSE_DTO crear(REQUEST_DTO request);

    RESPONSE_DTO actualizar(Long id, REQUEST_DTO request);

    void eliminar(Long id);

    /** Convierte una página ya consultada; cada elemento queda en caché, la página no. */
    List<RESPONSE_DTO> toResponsesCacheados(List<E> entities);
}
