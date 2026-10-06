package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.entity.*;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
public class EntityReferenceMapper {

    @PersistenceContext
    private EntityManager entityManager;

    @Named("clienteReference")
    public Cliente cliente(Long id) {
        return reference(Cliente.class, id);
    }

    @Named("productoReference")
    public Producto producto(Long id) {
        return reference(Producto.class, id);
    }

    @Named("categoriaReference")
    public Categoria categoria(Long id) {
        return reference(Categoria.class, id);
    }

    @Named("marcaReference")
    public Marca marca(Long id) {
        return reference(Marca.class, id);
    }

    @Named("timbradoReference")
    public Timbrado timbrado(Long id) {
        return reference(Timbrado.class, id);
    }

    @Named("medioPagoReference")
    public MedioPago medioPago(Long id) {
        return reference(MedioPago.class, id);
    }

    @Named("depositoReference")
    public Deposito deposito(Long id) {
        return reference(Deposito.class, id);
    }

    @Named("empleadoReference")
    public Empleado empleado(Long id) {
        return reference(Empleado.class, id);
    }

    private <E extends BaseEntity> E reference(Class<E> type, Long id) {
        return id == null ? null : entityManager.getReference(type, id);
    }
}
