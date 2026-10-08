package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.BaseEntity;
import com.distrimarket.commons.model.SoftDeletable;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.BaseMapper;
import com.distrimarket.ms.featurec.repository.BaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

public abstract class AbstractBaseService<
        E extends BaseEntity,
        REQUEST_DTO,
        RESPONSE_DTO,
        R extends BaseRepository<E>,
        M extends BaseMapper<REQUEST_DTO, RESPONSE_DTO, E>>
        implements BaseService<E, REQUEST_DTO, RESPONSE_DTO> {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final R repository;
    protected final M mapper;
    private final String resourceName;

    protected AbstractBaseService(R repository, M mapper, String resourceName) {
        this.repository = repository;
        this.mapper = mapper;
        this.resourceName = resourceName;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<E> list(Pageable pageable) {
        return repository.findAll(activeOnly(), pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<E> search(Specification<E> specification, Pageable pageable) {
        Specification<E> combined = activeOnly();
        if (specification != null) {
            combined = combined.and(specification);
        }
        return repository.findAll(combined, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public E get(Long id) {
        validarId(id);
        E entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
        return validarActivo(entity, id);
    }

    @Override
    @Transactional
    public E create(E entity) {
        validar(entity);
        entity.setId(null);
        if (entity instanceof SoftDeletable softDeletable && softDeletable.getActivo() == null) {
            softDeletable.setActivo(true);
        }
        log.info("Creando {}", resourceName);
        return repository.save(entity);
    }

    @Override
    @Transactional
    public E update(Long id, E changes) {
        E entity = get(id);
        copiarCambios(entity, changes);
        validar(entity);
        log.info("Actualizando {} {}", resourceName, id);
        return repository.save(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        E entity = get(id);
        if (entity instanceof SoftDeletable softDeletable) {
            softDeletable.setActivo(false);
        } else {
            marcarInactivo(entity);
        }
        repository.save(entity);
        log.info("{} {} marcado como inactivo", resourceName, id);
    }

    protected E validarActivo(E entity, Long id) {
        if (entity instanceof SoftDeletable softDeletable
                && Boolean.FALSE.equals(softDeletable.getActivo())) {
            throw new ResourceNotFoundException(resourceName, id);
        }
        return entity;
    }

    private Specification<E> activeOnly() {
        return (root, query, builder) -> {
            Class<?> entityType = root.getJavaType();
            if (entityType == null || !SoftDeletable.class.isAssignableFrom(entityType)) {
                return builder.conjunction();
            }
            var active = root.<Boolean>get("activo");
            return builder.or(builder.isNull(active), builder.isTrue(active));
        };
    }

    protected RESPONSE_DTO toResponse(E entity) {
        return mapper.toDto(entity);
    }

    protected E toEntity(REQUEST_DTO request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es obligatorio.");
        }
        return mapper.toEntity(request);
    }

    protected void validar(E entity) {
        if (entity == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es obligatorio.");
        }
    }

    protected void validarId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("El ID es obligatorio.");
        }
        if (id < 1) {
            throw new IllegalArgumentException("El ID debe ser un número positivo.");
        }
    }

    protected abstract void copiarCambios(E target, E source);

    protected abstract void marcarInactivo(E entity);
}
