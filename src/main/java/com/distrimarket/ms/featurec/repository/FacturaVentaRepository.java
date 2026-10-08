package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.FacturaVenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FacturaVentaRepository extends BaseRepository<FacturaVenta> {

    @Override
    @EntityGraph(attributePaths = {
            "cliente", "cliente.persona",
            "empleado", "empleado.persona",
            "deposito", "medioPago", "timbrado"
    })
    Page<FacturaVenta> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "cliente", "cliente.persona",
            "empleado", "empleado.persona",
            "deposito", "medioPago", "timbrado"
    })
    Page<FacturaVenta> findAll(Specification<FacturaVenta> specification, Pageable pageable);

    @Query("""
            select distinct factura
            from FacturaVenta factura
            left join fetch factura.detalles detalle
            left join fetch detalle.producto
            left join fetch factura.cliente cliente
            left join fetch cliente.persona
            left join fetch factura.empleado empleado
            left join fetch empleado.persona
            left join fetch factura.deposito
            left join fetch factura.medioPago
            left join fetch factura.timbrado
            where factura.id in :ids
            """)
    List<FacturaVenta> findDetailedByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select distinct factura
            from FacturaVenta factura
            left join fetch factura.detalles detalle
            left join fetch detalle.producto
            left join fetch factura.cliente cliente
            left join fetch cliente.persona
            left join fetch factura.empleado empleado
            left join fetch empleado.persona
            left join fetch factura.deposito
            left join fetch factura.medioPago
            left join fetch factura.timbrado
            where factura.id = :id
            """)
    Optional<FacturaVenta> findDetailedById(@Param("id") Long id);

}
