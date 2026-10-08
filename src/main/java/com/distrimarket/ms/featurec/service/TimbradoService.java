package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.commons.dto.TimbradoCreateDTO;
import com.distrimarket.commons.dto.TimbradoDTO;
import com.distrimarket.ms.featurec.repository.TimbradoRepository;
import com.distrimarket.ms.featurec.mapper.TimbradoMapper;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.config.SearchQuerySupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class TimbradoService extends AbstractBaseService<
        Timbrado, TimbradoCreateDTO, TimbradoDTO, TimbradoRepository, TimbradoMapper> {

    public TimbradoService(TimbradoRepository repository, TimbradoMapper mapper) {
        super(repository, mapper, "Timbrado");
    }

    @Override
    protected void copiarCambios(Timbrado target, Timbrado source) {
        target.setNumeroTimbrado(source.getNumeroTimbrado());
        target.setFechaInicio(source.getFechaInicio());
        target.setFechaVencimiento(source.getFechaVencimiento());
        target.setPuntoExpedicion(source.getPuntoExpedicion());
        target.setSucursal(source.getSucursal());
        target.setActivo(source.getActivo());
    }

    @Override
    protected void marcarInactivo(Timbrado entity) {
        entity.setActivo(false);
    }

    @Override
    protected Timbrado validarActivo(Timbrado entity, Long id) {
        if (Boolean.FALSE.equals(entity.getActivo())) {
            throw new ResourceNotFoundException("Timbrado", id);
        }
        return entity;
    }

    @Override
    protected void validar(Timbrado entity) {
        super.validar(entity);
        if (entity.getNumeroTimbrado() == null || entity.getNumeroTimbrado().isBlank()) {
            throw new IllegalArgumentException("El número de timbrado es obligatorio.");
        }
        if (entity.getFechaInicio() == null || entity.getFechaVencimiento() == null) {
            throw new IllegalArgumentException("El período de vigencia del timbrado es obligatorio.");
        }
        if (entity.getFechaVencimiento().isBefore(entity.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de vencimiento no puede ser anterior a la fecha de inicio.");
        }
        if (entity.getActivo() == null) {
            entity.setActivo(true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Timbrado> list(Pageable pageable) {
        return search((root, query, builder) -> builder.isTrue(root.get("activo")), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Timbrado> search(String query, Pageable pageable) {
        Specification<Timbrado> specification = (root, criteriaQuery, builder) ->
                builder.isTrue(root.get("activo"));
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, criteriaQuery, builder) -> {
                var matches = new java.util.ArrayList<Predicate>();
                matches.add(builder.like(builder.lower(root.get("numeroTimbrado")), pattern));
                matches.add(builder.like(builder.lower(root.get("puntoExpedicion")), pattern));
                matches.add(builder.like(builder.lower(root.get("sucursal")), pattern));
                SearchQuerySupport.parseDate(query).ifPresent(date -> {
                    matches.add(builder.equal(root.get("fechaInicio"), date));
                    matches.add(builder.equal(root.get("fechaVencimiento"), date));
                });
                try {
                    matches.add(builder.equal(root.get("id"), Long.valueOf(query.trim())));
                } catch (NumberFormatException ignored) {
                    // The query is not a numeric timbrado ID.
                }
                return builder.or(matches.toArray(Predicate[]::new));
            });
        }
        return search(specification, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Timbrado getForRead(Long id) {
        return get(id);
    }
}
