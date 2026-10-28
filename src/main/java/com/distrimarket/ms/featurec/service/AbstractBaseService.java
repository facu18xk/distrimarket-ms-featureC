package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.BaseEntity;
import com.distrimarket.commons.model.SoftDeletable;
import com.distrimarket.ms.featurec.config.DomainCache;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.BaseMapper;
import com.distrimarket.ms.featurec.repository.BaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import com.distrimarket.commons.service.CacheEnLote;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.cache.CacheManager;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.Collection;
import java.util.List;

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
    private DomainCache domainCache;

    protected AbstractBaseService(R repository, M mapper, String resourceName) {
        this.repository = repository;
        this.mapper = mapper;
        this.resourceName = resourceName;
    }

    @Autowired(required = false)
    public void setDomainCache(DomainCache domainCache) {
        this.domainCache = domainCache;
    }

    // ------------------------------------------------------------------
    // Operaciones con DTO. Cada servicio las redefine solo para declarar su
    // política de caché (@Cacheable, @CachePut, @CacheEvict) en su dominio.
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public RESPONSE_DTO obtener(Long id) {
        return toResponse(getForRead(id));
    }

    @Override
    @Transactional
    public RESPONSE_DTO crear(REQUEST_DTO request) {
        return toResponse(create(toEntity(request)));
    }

    @Override
    @Transactional
    public RESPONSE_DTO actualizar(Long id, REQUEST_DTO request) {
        return toResponse(update(id, toEntity(request)));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        delete(id);
    }

    @Override
    public List<RESPONSE_DTO> toResponsesCacheados(List<E> entities) {
        List<RESPONSE_DTO> responses = toResponses(entities);
        String cacheName = cacheName();
        if (domainCache != null && cacheName != null) {
            // La página no se cachea (depende de filtros y orden); cada elemento sí, por su id.
            domainCache.putAll(cacheName, responses, this::cacheKey);
        }
        return responses;
    }

    public List<RESPONSE_DTO> toResponses(List<E> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------
    // Listados desde la caché, objeto por objeto (ver CacheEnLote):
    //   1. de la base, solo los ids de la página (consulta liviana, con filtros y orden);
    //   2. cada id se busca en Redis (GET);
    //   3. los que faltan se traen de la base en UNA consulta y se guardan (SET).
    // La segunda vez que se pide la misma página: solo GET con HIT, sin SET.
    // ------------------------------------------------------------------

    @PersistenceContext
    private EntityManager entityManagerBase;
    private CacheManager cacheManager;

    @Autowired(required = false)
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /** Página de DTO a partir de un filtro: ids de la base y objetos de la caché. */
    @Transactional(readOnly = true)
    public Page<RESPONSE_DTO> buscarDesdeCache(Specification<E> specification, Pageable pageable) {
        Page<Long> ids = buscarIds(specification, pageable);
        String dominio = cacheName();
        return CacheEnLote.pagina(ids,
                dominio != null && cacheManager != null ? cacheManager.getCache(dominio) : null,
                faltan -> toResponses(cargarPorIds(faltan)),
                dto -> (Long) cacheKey(dto));
    }

    /**
     * Objetos de la base para los ids que no estaban en caché, en una sola consulta. Cada servicio
     * puede redefinirlo para traer también sus relaciones (EntityGraph o join fetch).
     */
    protected List<E> cargarPorIds(Collection<Long> ids) {
        return repository.findAllById(ids);
    }

    /** Solo los ids de la página (activos + filtro), con el mismo orden y total que la consulta completa. */
    @Transactional(readOnly = true)
    public Page<Long> buscarIds(Specification<E> specification, Pageable pageable) {
        // Desempate por id: con valores de orden repetidos, las páginas no se repiten ni saltean filas.
        pageable = com.distrimarket.commons.service.Paginas.conDesempatePorId(pageable);
        Class<E> tipo = tipoEntidad();
        Specification<E> filtro = specification == null ? activeOnly() : activeOnly().and(specification);
        CriteriaBuilder builder = entityManagerBase.getCriteriaBuilder();

        CriteriaQuery<Long> consulta = builder.createQuery(Long.class);
        Root<E> root = consulta.from(tipo);
        Predicate condicion = filtro.toPredicate(root, consulta, builder);
        consulta.select(root.get("id"));
        if (condicion != null) {
            consulta.where(condicion);
        }
        consulta.orderBy(QueryUtils.toOrders(pageable.getSort(), root, builder));
        TypedQuery<Long> ids = entityManagerBase.createQuery(consulta);
        if (pageable.isPaged()) {
            ids.setFirstResult((int) pageable.getOffset());
            ids.setMaxResults(pageable.getPageSize());
        }
        List<Long> contenido = ids.getResultList();

        return PageableExecutionUtils.getPage(contenido, pageable, () -> {
            CriteriaQuery<Long> conteo = builder.createQuery(Long.class);
            Root<E> raiz = conteo.from(tipo);
            Predicate condicionConteo = filtro.toPredicate(raiz, conteo, builder);
            conteo.select(builder.count(raiz));
            if (condicionConteo != null) {
                conteo.where(condicionConteo);
            }
            return entityManagerBase.createQuery(conteo).getSingleResult();
        });
    }

    @SuppressWarnings("unchecked")
    private Class<E> tipoEntidad() {
        Class<?>[] tipos = GenericTypeResolver.resolveTypeArguments(getClass(), AbstractBaseService.class);
        if (tipos == null || tipos[0] == null) {
            throw new IllegalStateException("No se pudo determinar la entidad de " + getClass().getSimpleName());
        }
        return (Class<E>) tipos[0];
    }

    /** Dominio de caché del recurso; null si no se cachea. */
    protected String cacheName() {
        return null;
    }

    /** Clave de caché de un DTO (su id). */
    protected Object cacheKey(RESPONSE_DTO response) {
        return null;
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
        Page<E> page = repository.findAll(combined, pageable);
        log.debug("Búsqueda de {}: página={} tamaño={} total={}",
                resourceName, page.getNumber(), page.getSize(), page.getTotalElements());
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public E get(Long id) {
        validarId(id);
        log.debug("Buscando {} {}", resourceName, id);
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
