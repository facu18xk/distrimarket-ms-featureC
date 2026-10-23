package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.commons.entity.FacturaVentaDetalle;
import com.distrimarket.commons.entity.Producto;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import com.distrimarket.ms.featurec.repository.FacturaVentaRepository;
import com.distrimarket.ms.featurec.repository.MedioPagoRepository;
import jakarta.persistence.EntityManager;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaVentaServiceSearchTest {
    @Mock private FacturaVentaRepository repository;
    @Mock private FacturaVentaMapper mapper;
    @Mock private MedioPagoRepository medioPagoRepository;
    @Mock private InventarioService inventarioService;

    @Mock private EntityManager entityManager;

    private FacturaVentaService service;

    @BeforeEach
    void setUp() {
        service = new FacturaVentaService(
                repository, mapper,
                medioPagoRepository, inventarioService, entityManager);
    }

    @Test
    void searchMatchesInvoiceNumberStateAndCustomerName() {
        Pageable pageable = PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "id"));
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        service.search(" venta ", pageable);

        ArgumentCaptor<Specification<FacturaVenta>> specification = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specification.capture(), eq(pageable));

        Root<FacturaVenta> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        Path<Object> cliente = mock(Path.class);
        Path<Object> persona = mock(Path.class);
        when(root.get("numeroFactura")).thenReturn(mock(Path.class));
        when(root.get("estado")).thenReturn(mock(Path.class));
        when(root.get("cliente")).thenReturn(cliente);
        when(cliente.get("persona")).thenReturn(persona);
        when(persona.get("nombreCompleto")).thenReturn(mock(Path.class));
        when(builder.conjunction()).thenReturn(predicate);
        when(builder.lower(any(Expression.class))).thenReturn(mock(Expression.class));
        when(builder.like(any(Expression.class), eq("%venta%"))).thenReturn(predicate);
        when(builder.or(any(Predicate[].class))).thenReturn(predicate);
        when(builder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        assertNotNull(specification.getValue().toPredicate(root, query, builder));
        verify(builder, org.mockito.Mockito.times(3)).like(any(Expression.class), eq("%venta%"));
        verify(builder).or(any(Predicate[].class));
        verify(builder, org.mockito.Mockito.times(2)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void numericQueryMatchesInvoiceNumberDigitsWithoutSeparators() {
        Pageable pageable = PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "id"));
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));
        service.search("234", pageable);

        ArgumentCaptor<Specification<FacturaVenta>> specification = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specification.capture(), eq(pageable));

        Root<FacturaVenta> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        Path<Object> client = mock(Path.class);
        Path<Object> person = mock(Path.class);
        Expression<String> normalizedNumber = mock(Expression.class);
        when(root.get("numeroFactura")).thenReturn(mock(Path.class));
        when(root.get("estado")).thenReturn(mock(Path.class));
        when(root.get("cliente")).thenReturn(client);
        when(client.get("persona")).thenReturn(person);
        when(person.get("nombreCompleto")).thenReturn(mock(Path.class));
        when(builder.conjunction()).thenReturn(predicate);
        when(builder.lower(any(Expression.class))).thenReturn(mock(Expression.class));
        when(builder.function(eq("regexp_replace"), eq(String.class), any(Expression[].class)))
                .thenReturn(normalizedNumber);
        when(builder.like(any(Expression.class), any(String.class))).thenReturn(predicate);
        when(builder.or(any(Predicate[].class))).thenReturn(predicate);
        when(builder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        assertNotNull(specification.getValue().toPredicate(root, query, builder));
        verify(builder).like(normalizedNumber, "%234%");
        verify(builder).function(eq("regexp_replace"), eq(String.class), any(Expression[].class));
    }

    @Test
    void dateQueryMatchesInvoiceIssueDate() {
        Pageable pageable = PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "id"));
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));
        service.search("06/10/2026", pageable);

        ArgumentCaptor<Specification<FacturaVenta>> specification = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specification.capture(), eq(pageable));

        Root<FacturaVenta> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        Path<Object> client = mock(Path.class);
        Path<Object> person = mock(Path.class);
        when(root.get("numeroFactura")).thenReturn(mock(Path.class));
        when(root.get("estado")).thenReturn(mock(Path.class));
        when(root.get("cliente")).thenReturn(client);
        when(client.get("persona")).thenReturn(person);
        when(person.get("nombreCompleto")).thenReturn(mock(Path.class));
        when(root.get("fechaEmision")).thenReturn(mock(Path.class));
        when(builder.conjunction()).thenReturn(predicate);
        when(builder.lower(any(Expression.class))).thenReturn(mock(Expression.class));
        when(builder.like(any(Expression.class), any(String.class))).thenReturn(predicate);
        when(builder.equal(any(Expression.class), eq(LocalDate.of(2026, 10, 6)))).thenReturn(predicate);
        lenient().when(builder.greaterThanOrEqualTo(
                nullable(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        lenient().when(builder.lessThan(nullable(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(builder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(builder.or(any(Predicate[].class))).thenReturn(predicate);

        assertNotNull(specification.getValue().toPredicate(root, query, builder));
        verify(builder).equal(any(Expression.class), eq(LocalDate.of(2026, 10, 6)));
    }

    @Test
    void searchReturnsOnlyHeadersAndKeepsOrderAndTotals() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "id"));
        FacturaVenta firstHeader = invoice(1L);
        FacturaVenta secondHeader = invoice(2L);
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(firstHeader, secondHeader), pageable, 2));

        Page<FacturaVenta> results = service.search((String) null, pageable);

        // El listado son cabeceras: las líneas se cargan solo al pedir una factura por id.
        assertEquals(2, results.getTotalElements());
        assertSame(firstHeader, results.getContent().get(0));
        assertSame(secondHeader, results.getContent().get(1));
        verify(repository, never()).findDetailedById(any());
    }

    private FacturaVenta invoice(Long id) {
        FacturaVenta invoice = new FacturaVenta();
        invoice.setId(id);
        return invoice;
    }

    private FacturaVenta invoiceWithDetail(Long invoiceId, Long detailId) {
        FacturaVenta invoice = invoice(invoiceId);
        FacturaVentaDetalle detail = new FacturaVentaDetalle();
        detail.setId(detailId);
        detail.setProducto(new Producto());
        invoice.setDetalles(List.of(detail));
        return invoice;
    }

}
