package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.BaseEntity;
import com.distrimarket.commons.model.SoftDeletable;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.BaseMapper;
import com.distrimarket.ms.featurec.repository.BaseRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoftDeletableServiceTest {

    @Mock
    private BaseRepository<TestEntity> repository;
    @Mock
    private BaseMapper<String, String, TestEntity> mapper;

    private TestService service;

    @BeforeEach
    void setUp() {
        service = new TestService(repository, mapper);
    }

    @Test
    void deleteMarksSoftDeletableEntityInactive() {
        TestEntity entity = new TestEntity();
        entity.setId(10L);
        entity.setActivo(true);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        service.delete(10L);

        assertFalse(entity.getActivo());
        verify(repository).save(entity);
    }

    @Test
    void getHidesSoftDeletedEntities() {
        TestEntity entity = new TestEntity();
        entity.setId(10L);
        entity.setActivo(false);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.get(10L));

        assertEquals("Recurso con ID 10 no encontrado", exception.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void searchFiltersOutSoftDeletedEntities() {
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(Page.empty(pageable));

        service.search(null, pageable);

        var specification = org.mockito.ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specification.capture(), org.mockito.ArgumentMatchers.eq(pageable));

        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<Boolean> active = mock(Path.class);
        Predicate nullPredicate = mock(Predicate.class);
        Predicate activePredicate = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);
        doReturn(TestEntity.class).when(root).getJavaType();
        when(root.<Boolean>get("activo")).thenReturn(active);
        when(builder.isNull(active)).thenReturn(nullPredicate);
        when(builder.isTrue(active)).thenReturn(activePredicate);
        when(builder.or(nullPredicate, activePredicate)).thenReturn(combined);

        assertNotNull(specification.getValue().toPredicate(root, query, builder));
        verify(builder).or(nullPredicate, activePredicate);
    }

    @Test
    void createDefaultsSoftDeletableEntitiesToActive() {
        TestEntity entity = new TestEntity();
        when(repository.save(entity)).thenReturn(entity);

        service.create(entity);

        assertEquals(Boolean.TRUE, entity.getActivo());
        verify(repository).save(entity);
    }

    private static class TestEntity extends BaseEntity implements SoftDeletable {
        private Boolean activo;

        @Override
        public Boolean getActivo() {
            return activo;
        }

        @Override
        public void setActivo(Boolean activo) {
            this.activo = activo;
        }
    }

    private static class TestService extends AbstractBaseService<
            TestEntity, String, String, BaseRepository<TestEntity>, BaseMapper<String, String, TestEntity>> {

        private TestService(
                BaseRepository<TestEntity> repository,
                BaseMapper<String, String, TestEntity> mapper) {
            super(repository, mapper, "Recurso");
        }

        @Override
        protected void copiarCambios(TestEntity target, TestEntity source) {
        }

        @Override
        protected void marcarInactivo(TestEntity entity) {
            throw new UnsupportedOperationException();
        }
    }
}
