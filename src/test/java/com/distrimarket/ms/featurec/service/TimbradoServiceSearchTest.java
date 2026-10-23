package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.TimbradoMapper;
import com.distrimarket.ms.featurec.repository.TimbradoRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.util.Optional.of;

@ExtendWith(MockitoExtension.class)
class TimbradoServiceSearchTest {
    @Mock private TimbradoRepository repository;
    @Mock private TimbradoMapper mapper;

    private TimbradoService service;

    @BeforeEach
    void setUp() {
        service = new TimbradoService(repository, mapper);
    }

    @Test
    void searchMatchesTimbradoNumberBranchAndOfficeAndOnlyActiveRecords() {
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        service.search(" 001 ", pageable);

        ArgumentCaptor<Specification<Timbrado>> specification = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specification.capture(), eq(pageable));

        Root<Timbrado> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        Path<Boolean> active = mock(Path.class);
        when(root.<Boolean>get("activo")).thenReturn(active);
        when(root.<Object>get("numeroTimbrado")).thenReturn(mock(Path.class));
        when(root.<Object>get("puntoExpedicion")).thenReturn(mock(Path.class));
        when(root.<Object>get("sucursal")).thenReturn(mock(Path.class));
        when(builder.isTrue(active)).thenReturn(predicate);
        when(builder.lower(any(Expression.class))).thenReturn(mock(Expression.class));
        when(builder.like(any(Expression.class), eq("%001%"))).thenReturn(predicate);
        when(builder.or(any(Predicate[].class))).thenReturn(predicate);
        when(builder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        assertNotNull(specification.getValue().toPredicate(root, query, builder));
        verify(builder).isTrue(active);
        verify(builder, org.mockito.Mockito.times(3)).like(any(Expression.class), eq("%001%"));
        verify(builder).or(any(Predicate[].class));
    }

    @Test
    void createIgnoresCallerProvidedIdAndLetsIdentityGenerateIt() {
        Timbrado timbrado = new Timbrado();
        timbrado.setId(700L);
        timbrado.setNumeroTimbrado("12345678");
        timbrado.setFechaInicio(java.time.LocalDate.now());
        timbrado.setFechaVencimiento(java.time.LocalDate.now().plusDays(30));
        when(repository.save(timbrado)).thenReturn(timbrado);

        service.create(timbrado);

        assertNull(timbrado.getId());
        verify(repository).save(timbrado);
    }

    @Test
    void createRejectsAnInvalidValidityPeriod() {
        Timbrado timbrado = new Timbrado();
        timbrado.setNumeroTimbrado("12345678");
        timbrado.setFechaInicio(java.time.LocalDate.now());
        timbrado.setFechaVencimiento(java.time.LocalDate.now().minusDays(1));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(timbrado));

        assertEquals("La fecha de vencimiento no puede ser anterior a la fecha de inicio.", exception.getMessage());
        verify(repository, never()).save(timbrado);
    }

    @Test
    void inactiveTimbradoCannotBeReadUpdatedOrDeleted() {
        Timbrado inactive = new Timbrado();
        inactive.setId(31L);
        inactive.setActivo(false);
        when(repository.findById(31L)).thenReturn(of(inactive));

        assertThrows(ResourceNotFoundException.class, () -> service.getForRead(31L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(31L, new Timbrado()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(31L));
        verify(repository, never()).save(inactive);
    }
}
