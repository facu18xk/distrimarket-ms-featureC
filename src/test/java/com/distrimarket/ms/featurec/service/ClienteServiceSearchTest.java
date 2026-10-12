package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Persona;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.ClienteMapper;
import com.distrimarket.ms.featurec.repository.ClienteRepository;
import com.distrimarket.ms.featurec.repository.PersonaRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ClienteServiceSearchTest {
    @Mock private ClienteRepository repository;
    @Mock private ClienteMapper mapper;
    @Mock private PersonaRepository personaRepository;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(repository, mapper, personaRepository);
    }

    @Test
    void searchMatchesNameDocumentsPhoneAndEmailAndOnlyActiveClients() {
        Pageable pageable = PageRequest.of(1, 25);
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        service.search(" Ana ", pageable);

        ArgumentCaptor<Specification<Cliente>> specification = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specification.capture(), eq(pageable));

        Root<Cliente> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        Path<Object> persona = mock(Path.class);
        Path<Boolean> activo = mock(Path.class);
        Path<Object> name = mock(Path.class);
        Path<Object> ci = mock(Path.class);
        Path<Object> ruc = mock(Path.class);
        Path<Object> phone = mock(Path.class);
        Path<Object> email = mock(Path.class);
        Fetch<Object, Object> fetch = mock(Fetch.class);
        doReturn(Cliente.class).when(query).getResultType();
        when(root.fetch("persona", JoinType.LEFT)).thenReturn(fetch);
        when(root.<Boolean>get("estado")).thenReturn(activo);
        when(root.<Object>get("persona")).thenReturn(persona);
        when(persona.get("nombreCompleto")).thenReturn(name);
        when(persona.get("ci")).thenReturn(ci);
        when(persona.get("ruc")).thenReturn(ruc);
        when(persona.get("telefono")).thenReturn(phone);
        when(persona.get("correo")).thenReturn(email);
        when(builder.isTrue(activo)).thenReturn(predicate);
        when(builder.lower(any(Expression.class))).thenReturn(mock(Expression.class));
        when(builder.like(any(Expression.class), eq("%ana%"))).thenReturn(predicate);
        when(builder.or(any(Predicate[].class))).thenReturn(predicate);
        when(builder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        assertNotNull(specification.getValue().toPredicate(root, query, builder));
        verify(builder).isTrue(activo);
        verify(builder, org.mockito.Mockito.times(5)).like(any(Expression.class), eq("%ana%"));
        verify(builder).or(any(Predicate[].class));
        verify(root).fetch("persona", JoinType.LEFT);
    }

    @Test
    void inactiveClientCannotBeReadUpdatedOrDeleted() {
        Cliente inactive = new Cliente();
        inactive.setId(31L);
        inactive.setEstado(false);
        when(repository.findById(31L)).thenReturn(java.util.Optional.of(inactive));

        assertThrows(ResourceNotFoundException.class, () -> service.getForRead(31L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(31L, new Cliente()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(31L));
        verify(repository, never()).save(any(Cliente.class));
    }

    @Test
    void updatePreservesExistingPersonaWhenRequestDoesNotCarryPersonaId() {
        Persona currentPersona = new Persona();
        currentPersona.setId(12L);
        currentPersona.setNombreCompleto("Ana Original");
        Cliente currentClient = new Cliente();
        currentClient.setId(7L);
        currentClient.setEstado(true);
        currentClient.setPersona(currentPersona);

        Persona requestedPersona = new Persona();
        requestedPersona.setNombreCompleto("Ana Actualizada");
        requestedPersona.setTipoPersona(com.distrimarket.commons.enums.TipoPersona.FISICA);
        requestedPersona.setCi("1234567");
        Cliente changes = new Cliente();
        changes.setPersona(requestedPersona);

        when(repository.findById(7L)).thenReturn(java.util.Optional.of(currentClient));
        when(repository.save(currentClient)).thenReturn(currentClient);

        service.update(7L, changes);

        assertEquals(12L, currentClient.getPersona().getId());
        assertEquals("Ana Actualizada", currentClient.getPersona().getNombreCompleto());
        verify(personaRepository).save(currentPersona);
        verify(repository).save(currentClient);
    }
}
