package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.MedioPagoMapper;
import com.distrimarket.ms.featurec.repository.MedioPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedioPagoServiceTest {
    @Mock private MedioPagoRepository repository;
    @Mock private MedioPagoMapper mapper;

    private MedioPagoService service;

    @BeforeEach
    void setUp() {
        service = new MedioPagoService(repository, mapper);
    }

    @Test
    void createTrimsNameAndDefaultsActive() {
        MedioPago medioPago = new MedioPago();
        medioPago.setNombre("  Efectivo  ");
        medioPago.setActivo(null);
        when(repository.save(medioPago)).thenReturn(medioPago);

        service.create(medioPago);

        assertEquals("Efectivo", medioPago.getNombre());
        assertTrue(medioPago.getActivo());
        verify(repository).save(medioPago);
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        MedioPago medioPago = new MedioPago();
        medioPago.setNombre("efectivo");
        when(repository.existsByNombreIgnoreCase("efectivo")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(medioPago));
        verify(repository, never()).save(any());
    }

    @Test
    void updateChecksDuplicateNameAndDoesNotAllowChangingActiveFlag() {
        MedioPago existing = new MedioPago();
        existing.setId(4L);
        existing.setNombre("Efectivo");
        existing.setActivo(true);
        MedioPago changes = new MedioPago();
        changes.setNombre("Tarjeta");
        changes.setActivo(false);
        when(repository.findById(4L)).thenReturn(Optional.of(existing));
        when(repository.existsByNombreIgnoreCaseAndIdNot("Tarjeta", 4L)).thenReturn(false);
        when(repository.save(existing)).thenReturn(existing);

        service.update(4L, changes);

        assertEquals("Tarjeta", existing.getNombre());
        assertTrue(existing.getActivo());
        verify(repository).existsByNombreIgnoreCaseAndIdNot("Tarjeta", 4L);
    }

    @Test
    void inactivePaymentMethodCannotBeReadUpdatedOrDeleted() {
        MedioPago inactive = new MedioPago();
        inactive.setId(31L);
        inactive.setActivo(false);
        when(repository.findById(31L)).thenReturn(Optional.of(inactive));

        assertThrows(ResourceNotFoundException.class, () -> service.getForRead(31L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(31L, new MedioPago()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(31L));
        verify(repository, never()).save(inactive);
    }

    @Test
    void searchUsesSpecificationAndReturnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        Page<MedioPago> result = service.search("efectivo", pageable);

        assertTrue(result.isEmpty());
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void deleteSoftDeletesAnActivePaymentMethod() {
        MedioPago active = new MedioPago();
        active.setId(9L);
        active.setActivo(true);
        when(repository.findById(9L)).thenReturn(Optional.of(active));
        when(repository.save(active)).thenReturn(active);

        service.delete(9L);

        assertFalse(active.getActivo());
        verify(repository).save(active);
    }
}
