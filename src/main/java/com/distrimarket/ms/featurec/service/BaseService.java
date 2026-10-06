package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.BaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
}
