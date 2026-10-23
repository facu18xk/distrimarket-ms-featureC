package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.ms.featurec.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import com.distrimarket.commons.dto.TimbradoCreateDTO;
import com.distrimarket.commons.dto.TimbradoDTO;
import com.distrimarket.ms.featurec.repository.TimbradoRepository;
import com.distrimarket.ms.featurec.mapper.TimbradoMapper;
import com.distrimarket.ms.featurec.exception.ConflictException;
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

    // --- Caché (dominio "timbrado", persistente) ---

    @Override
    protected String cacheName() {
        return CacheConfig.TIMBRADO;
    }

    @Override
    protected Object cacheKey(TimbradoDTO response) {
        return response.getIdTimbrado();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.TIMBRADO, key = "#id")
    public TimbradoDTO obtener(Long id) {
        return super.obtener(id);
    }

    @Override
    @CachePut(cacheNames = CacheConfig.TIMBRADO, key = "#result.idTimbrado")
    public TimbradoDTO crear(TimbradoCreateDTO request) {
        return super.crear(request);
    }

    // Las facturas en caché muestran el timbrado: esa sección también se invalida.
    @Override
    @Caching(
            put = @CachePut(cacheNames = CacheConfig.TIMBRADO, key = "#id"),
            evict = @CacheEvict(cacheNames = CacheConfig.FACTURA_VENTA, allEntries = true))
    public TimbradoDTO actualizar(Long id, TimbradoCreateDTO request) {
        return super.actualizar(id, request);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.TIMBRADO, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.FACTURA_VENTA, allEntries = true)})
    public void eliminar(Long id) {
        super.eliminar(id);
    }

    @Override
    protected void copiarCambios(Timbrado target, Timbrado source) {
        target.setNumeroTimbrado(source.getNumeroTimbrado());
        target.setFechaInicio(source.getFechaInicio());
        target.setFechaVencimiento(source.getFechaVencimiento());
        target.setPuntoExpedicion(source.getPuntoExpedicion());
        target.setSucursal(source.getSucursal());
        // El campo activo no se modifica por PUT: la baja lógica solo ocurre con DELETE.
    }

    @Override
    @Transactional
    public Timbrado create(Timbrado entity) {
        if (entity != null) {
            entity.setActivo(true);
        }
        return super.create(entity);
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
        log.debug("Validando vigencia del timbrado {}: {} a {}",
                entity.getNumeroTimbrado(), entity.getFechaInicio(), entity.getFechaVencimiento());
        if (entity.getFechaVencimiento().isBefore(entity.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de vencimiento no puede ser anterior a la fecha de inicio.");
        }
        String numeroTimbrado = entity.getNumeroTimbrado().trim();
        entity.setNumeroTimbrado(numeroTimbrado);
        // El número es único en la base aunque el timbrado anterior esté dado de baja.
        boolean duplicado = entity.getId() == null
                ? repository.existsByNumeroTimbrado(numeroTimbrado)
                : repository.existsByNumeroTimbradoAndIdNot(numeroTimbrado, entity.getId());
        if (duplicado) {
            log.warn("Intento de registrar un número de timbrado duplicado: {}", numeroTimbrado);
            throw new ConflictException("Ya existe un timbrado con ese número.");
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
