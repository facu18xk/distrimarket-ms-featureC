package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Persona;
import com.distrimarket.commons.dto.ClienteRequestDTO;
import com.distrimarket.commons.dto.ClienteResponseDTO;
import com.distrimarket.ms.featurec.repository.ClienteRepository;
import com.distrimarket.ms.featurec.repository.PersonaRepository;
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

    private Persona resolverPersona(Persona persona) {
        if (persona == null) {
            return null;
        }
        if (persona.getId() == null) {
            return personaRepository.save(persona);
        }
        return personaRepository.findById(persona.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Persona", persona.getId()));
    }

    @Override
    @Transactional
    public Cliente create(Cliente entity) {
        if (entity != null) {
            entity.setPersona(resolverPersona(entity.getPersona()));
        }
        return super.create(entity);
    }

    @Override
    protected void copiarCambios(Cliente target, Cliente source) {
        if (source.getPersona() != null) {
            if (source.getPersona().getId() == null && target.getPersona() != null) {
                copiarDatosPersona(target.getPersona(), source.getPersona());
                personaRepository.save(target.getPersona());
            } else {
                target.setPersona(resolverPersona(source.getPersona()));
            }
        }
        if (source.getEstado() != null) {
            target.setEstado(source.getEstado());
        }
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

    public Page<Cliente> search(String query, Pageable pageable) {
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
        return search(specification, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente getForRead(Long id) {
        return get(id);
    }
}
