package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Persona;
import com.distrimarket.commons.enums.TipoPersona;
import com.distrimarket.commons.dto.ClientePatchRequestDTO;
import com.distrimarket.commons.dto.ClienteRequestDTO;
import com.distrimarket.ms.featurec.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import com.distrimarket.commons.dto.ClienteResponseDTO;
import com.distrimarket.ms.featurec.repository.ClienteRepository;
import com.distrimarket.ms.featurec.repository.PersonaRepository;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.config.SearchQuerySupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import com.distrimarket.ms.featurec.mapper.ClienteMapper;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ClienteService extends AbstractBaseService<
        Cliente, ClienteRequestDTO, ClienteResponseDTO, ClienteRepository, ClienteMapper> {

    private final PersonaRepository personaRepository;

    public ClienteService(ClienteRepository repository,
                          ClienteMapper mapper,
                          PersonaRepository personaRepository) {
        super(repository, mapper, "Cliente");
        this.personaRepository = personaRepository;
    }

    // --- Caché (dominio "cliente", TTL configurable) ---

    @Override
    protected String cacheName() {
        return CacheConfig.CLIENTE;
    }

    @Override
    protected Object cacheKey(ClienteResponseDTO response) {
        return response.getIdCliente();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CLIENTE, key = "#id")
    public ClienteResponseDTO obtener(Long id) {
        return super.obtener(id);
    }

    @Override
    @CachePut(cacheNames = CacheConfig.CLIENTE, key = "#result.idCliente")
    public ClienteResponseDTO crear(ClienteRequestDTO request) {
        return super.crear(request);
    }

    // Las facturas en caché muestran al cliente: esa sección también se invalida.
    @Override
    @Caching(
            put = @CachePut(cacheNames = CacheConfig.CLIENTE, key = "#id"),
            evict = @CacheEvict(cacheNames = CacheConfig.FACTURA_VENTA, allEntries = true))
    public ClienteResponseDTO actualizar(Long id, ClienteRequestDTO request) {
        return super.actualizar(id, request);
    }

    @Caching(
            put = @CachePut(cacheNames = CacheConfig.CLIENTE, key = "#id"),
            evict = @CacheEvict(cacheNames = CacheConfig.FACTURA_VENTA, allEntries = true))
    @Transactional
    public ClienteResponseDTO parchear(Long id, ClientePatchRequestDTO request) {
        Persona cambios = request == null ? null : mapper.toPersona(request.getPersona());
        return toResponse(actualizarParcialmente(id, cambios));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CLIENTE, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.FACTURA_VENTA, allEntries = true)})
    public void eliminar(Long id) {
        super.eliminar(id);
    }

    private Persona resolverPersona(Persona persona) {
        if (persona == null) {
            return null;
        }
        if (persona.getId() == null) {
            log.debug("Registrando persona nueva para el cliente: {}", persona.getNombreCompleto());
            return personaRepository.save(persona);
        }
        log.debug("Asociando el cliente a la persona existente {}", persona.getId());
        return personaRepository.findById(persona.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Persona", persona.getId()));
    }

    @Override
    @Transactional
    public Cliente create(Cliente entity) {
        if (entity != null) {
            if (entity.getPersona() != null && entity.getPersona().getId() == null) {
                validarPersona(entity.getPersona());
                validarDocumentosUnicos(entity.getPersona().getCi(), entity.getPersona().getRuc(), null);
            }
            entity.setPersona(resolverPersona(entity.getPersona()));
            // Un cliente siempre nace activo; la baja lógica solo ocurre con DELETE.
            entity.setEstado(true);
        }
        return super.create(entity);
    }

    @Transactional
    public Cliente actualizarParcialmente(Long id, Persona cambiosPersona) {
        Cliente cliente = get(id);
        if (cambiosPersona != null && cliente.getPersona() != null) {
            Persona persona = cliente.getPersona();
            // Antes de tocar la entidad: una consulta posterior la enviaría a la base con el dato duplicado.
            validarDocumentosUnicos(cambiosPersona.getCi(), cambiosPersona.getRuc(), persona.getId());
            if (cambiosPersona.getTipoPersona() != null) {
                persona.setTipoPersona(cambiosPersona.getTipoPersona());
            }
            if (cambiosPersona.getNombreCompleto() != null) {
                persona.setNombreCompleto(cambiosPersona.getNombreCompleto());
            }
            if (cambiosPersona.getCi() != null) {
                persona.setCi(cambiosPersona.getCi());
            }
            if (cambiosPersona.getRuc() != null) {
                persona.setRuc(cambiosPersona.getRuc());
            }
            if (cambiosPersona.getTelefono() != null) {
                persona.setTelefono(cambiosPersona.getTelefono());
            }
            if (cambiosPersona.getCorreo() != null) {
                persona.setCorreo(cambiosPersona.getCorreo());
            }
            if (cambiosPersona.getDireccion() != null) {
                persona.setDireccion(cambiosPersona.getDireccion());
            }
            validarPersona(persona);
            personaRepository.save(persona);
        }
        log.info("Actualizando parcialmente Cliente {}", id);
        return repository.save(cliente);
    }

    // Misma regla que Persona valida al persistir, anticipada para responder 400 y no 500.
    private void validarPersona(Persona persona) {
        if (persona.getTipoPersona() == null) {
            throw new IllegalArgumentException("El tipo de persona es obligatorio.");
        }
        if (persona.getNombreCompleto() == null || persona.getNombreCompleto().isBlank()) {
            throw new IllegalArgumentException("El nombre completo de la persona es obligatorio.");
        }
        if (persona.getTipoPersona() == TipoPersona.JURIDICA
                && (persona.getRuc() == null || persona.getRuc().isBlank())) {
            throw new IllegalArgumentException("Una persona jurídica debe tener RUC.");
        }
        if (persona.getTipoPersona() == TipoPersona.FISICA
                && (persona.getCi() == null || persona.getCi().isBlank())) {
            throw new IllegalArgumentException("Una persona física debe tener CI.");
        }
    }

    // CI y RUC son únicos en la base; se avisa con un mensaje claro en vez del conflicto genérico.
    private void validarDocumentosUnicos(String ci, String ruc, Long idPersonaActual) {
        if (ci != null && !ci.isBlank() && (idPersonaActual == null
                ? personaRepository.existsByCi(ci)
                : personaRepository.existsByCiAndIdNot(ci, idPersonaActual))) {
            log.warn("Intento de registrar una persona con CI duplicado: {}", ci);
            throw new ConflictException("Ya existe una persona con el CI " + ci + ".");
        }
        if (ruc != null && !ruc.isBlank() && (idPersonaActual == null
                ? personaRepository.existsByRuc(ruc)
                : personaRepository.existsByRucAndIdNot(ruc, idPersonaActual))) {
            log.warn("Intento de registrar una persona con RUC duplicado: {}", ruc);
            throw new ConflictException("Ya existe una persona con el RUC " + ruc + ".");
        }
    }

    @Override
    protected void copiarCambios(Cliente target, Cliente source) {
        if (source.getPersona() != null) {
            if (source.getPersona().getId() == null && target.getPersona() != null) {
                validarPersona(source.getPersona());
                validarDocumentosUnicos(
                        source.getPersona().getCi(), source.getPersona().getRuc(), target.getPersona().getId());
                copiarDatosPersona(target.getPersona(), source.getPersona());
                personaRepository.save(target.getPersona());
            } else {
                target.setPersona(resolverPersona(source.getPersona()));
            }
        }
        // El estado no se modifica por PUT: la baja lógica solo ocurre con DELETE.
    }

    private void copiarDatosPersona(Persona target, Persona source) {
        target.setTipoPersona(source.getTipoPersona());
        target.setNombreCompleto(source.getNombreCompleto());
        target.setCi(source.getCi());
        target.setRuc(source.getRuc());
        target.setTelefono(source.getTelefono());
        target.setCorreo(source.getCorreo());
        target.setDireccion(source.getDireccion());
    }

    @Override
    protected void marcarInactivo(Cliente entity) {
        entity.setEstado(false);
    }

    @Override
    protected Cliente validarActivo(Cliente entity, Long id) {
        if (Boolean.FALSE.equals(entity.getEstado())) {
            throw new ResourceNotFoundException("Cliente", id);
        }
        return entity;
    }

    @Override
    protected void validar(Cliente entity) {
        super.validar(entity);
        if (entity.getPersona() == null) {
            throw new IllegalArgumentException("La persona asociada al cliente es obligatoria.");
        }
        if (entity.getEstado() == null) {
            entity.setEstado(true);
        }
    }

    /** Clientes que faltaban en caché, con su persona, en una sola consulta. */
    @Override
    protected java.util.List<Cliente> cargarPorIds(java.util.Collection<Long> ids) {
        return repository.findByIdIn(ids);
    }

    /** Filtro de la búsqueda por texto (solo activos). */
    public Specification<Cliente> filtro(String query) {
        Specification<Cliente> specification = (root, criteriaQuery, builder) -> {
            if (criteriaQuery.getResultType() != Long.class && criteriaQuery.getResultType() != long.class) {
                root.fetch("persona", jakarta.persistence.criteria.JoinType.LEFT);
            }
            return builder.isTrue(root.get("estado"));
        };
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, criteriaQuery, builder) -> {
                var matches = new java.util.ArrayList<Predicate>();
                matches.add(builder.like(builder.lower(root.get("persona").get("nombreCompleto")), pattern));
                matches.add(builder.like(builder.lower(root.get("persona").get("ci")), pattern));
                matches.add(builder.like(builder.lower(root.get("persona").get("ruc")), pattern));
                matches.add(builder.like(builder.lower(root.get("persona").get("telefono")), pattern));
                matches.add(builder.like(builder.lower(root.get("persona").get("correo")), pattern));

                try {
                    matches.add(builder.equal(root.get("id"), Long.valueOf(query.trim())));
                } catch (NumberFormatException ignored) {
                    // The query is not a numeric client ID.
                }

                SearchQuerySupport.parseDate(query).ifPresent(date -> {
                    LocalDateTime start = date.atStartOfDay();
                    LocalDateTime end = date.plusDays(1).atStartOfDay();
                    matches.add(builder.and(
                            builder.greaterThanOrEqualTo(root.get("fechaCreacion"), start),
                            builder.lessThan(root.get("fechaCreacion"), end)));
                    matches.add(builder.and(
                            builder.greaterThanOrEqualTo(root.get("fechaModificacion"), start),
                            builder.lessThan(root.get("fechaModificacion"), end)));
                });
                return builder.or(matches.toArray(Predicate[]::new));
            });
        }
        return specification;
    }

    @Transactional(readOnly = true)
    public Page<Cliente> search(String query, Pageable pageable) {
        return search(filtro(query), pageable);
    }

    /** Página para el listado: ids de la base y cada Cliente desde la caché (ver buscarDesdeCache). */
    @Transactional(readOnly = true)
    public Page<ClienteResponseDTO> buscar(String query, Pageable pageable) {
        return buscarDesdeCache(filtro(query), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente getForRead(Long id) {
        return get(id);
    }
}
