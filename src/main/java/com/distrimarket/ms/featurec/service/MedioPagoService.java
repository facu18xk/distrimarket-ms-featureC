package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.MedioPagoDTO;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.MedioPagoMapper;
import com.distrimarket.ms.featurec.repository.MedioPagoRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;

@Service
public class MedioPagoService extends AbstractBaseService<
        MedioPago, MedioPagoDTO, MedioPagoDTO, MedioPagoRepository, MedioPagoMapper> {

    public MedioPagoService(MedioPagoRepository repository, MedioPagoMapper mapper) {
        super(repository, mapper, "Medio de pago");
    }

    @Override
    protected void copiarCambios(MedioPago target, MedioPago source) {
        target.setNombre(source.getNombre());
    }

    @Override
    protected void marcarInactivo(MedioPago entity) {
        entity.setActivo(false);
    }

    @Override
    protected MedioPago validarActivo(MedioPago entity, Long id) {
        if (Boolean.FALSE.equals(entity.getActivo())) {
            throw new ResourceNotFoundException("Medio de pago", id);
        }
        return entity;
    }

    @Override
    protected void validar(MedioPago entity) {
        super.validar(entity);
        if (entity.getNombre() == null || entity.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del medio de pago es obligatorio.");
        }

        String nombre = entity.getNombre().trim();
        if (nombre.length() > 50) {
            throw new IllegalArgumentException("El nombre del medio de pago no puede superar 50 caracteres.");
        }
        entity.setNombre(nombre);

        boolean duplicate = entity.getId() == null
                ? repository.existsByNombreIgnoreCase(nombre)
                : repository.existsByNombreIgnoreCaseAndIdNot(nombre, entity.getId());
        if (duplicate) {
            throw new ConflictException("Ya existe un medio de pago con ese nombre.");
        }
        if (entity.getActivo() == null) {
            entity.setActivo(true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MedioPago> list(Pageable pageable) {
        return search((root, query, builder) -> builder.isTrue(root.get("activo")), pageable);
    }

    @Transactional(readOnly = true)
    public Page<MedioPago> search(String query, Pageable pageable) {
        Specification<MedioPago> specification = (root, criteriaQuery, builder) ->
                builder.isTrue(root.get("activo"));
        if (query != null && !query.isBlank()) {
            String normalizedQuery = query.trim();
            String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, criteriaQuery, builder) -> {
                var matches = new ArrayList<Predicate>();
                matches.add(builder.like(builder.lower(root.get("nombre")), pattern));
                try {
                    matches.add(builder.equal(root.get("id"), Long.valueOf(normalizedQuery)));
                } catch (NumberFormatException ignored) {
                    // The query is not a numeric payment-method ID.
                }
                return builder.or(matches.toArray(Predicate[]::new));
            });
        }
        return search(specification, pageable);
    }
}
