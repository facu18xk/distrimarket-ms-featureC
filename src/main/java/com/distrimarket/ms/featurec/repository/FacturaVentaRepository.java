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

    /**
     * @EntityGraph precarga en un solo SELECT con LEFT JOIN el cliente, la persona del cliente,
     * el medio de pago y el timbrado para el listado paginado de cabeceras (evitando 4*N consultas).
     * No incluye "detalles" para mantener la paginación SQL con LIMIT/OFFSET.
     */
    @Override
    @EntityGraph(attributePaths = {
            "cliente", "cliente.persona",
            "medioPago", "timbrado"
    })
    Page<FacturaVenta> findAll(Pageable pageable);

    /**
     * @EntityGraph precarga las asociaciones de cabecera cuando se filtra con Specification,
     * evitando consultas N+1 al construir FacturaVentaResumenDTO.
     */
    @Override
    @EntityGraph(attributePaths = {
            "cliente", "cliente.persona",
            "medioPago", "timbrado"
    })
    Page<FacturaVenta> findAll(Specification<FacturaVenta> specification, Pageable pageable);

    /**
     * @Query con LEFT JOIN FETCH trae la factura completa (cabecera, cliente, persona,
     * medio de pago, timbrado y todas las líneas de detalle) en una única consulta SQL.
     */
    @Query("""
            select distinct factura
            from FacturaVenta factura
            left join fetch factura.detalles detalle
            left join fetch factura.cliente cliente
            left join fetch cliente.persona
            left join fetch factura.medioPago
            left join fetch factura.timbrado
            where factura.id = :id
            """)
    Optional<FacturaVenta> findDetailedById(@Param("id") Long id);

    /**
     * Facturas completas (cabecera, cliente, persona, medio de pago, timbrado y líneas) de los ids
     * que faltaban en caché, en una sola consulta.
     */
    @Query("""
            select distinct factura
            from FacturaVenta factura
            left join fetch factura.detalles detalle
            left join fetch factura.cliente cliente
            left join fetch cliente.persona
            left join fetch factura.medioPago
            left join fetch factura.timbrado
            where factura.id in :ids
            """)
    List<FacturaVenta> findDetailedByIdIn(@Param("ids") Collection<Long> ids);

    boolean existsByNumeroFacturaAndTimbrado_Id(String numeroFactura, Long timbradoId);

    boolean existsByNumeroFacturaAndTimbrado_IdAndIdNot(String numeroFactura, Long timbradoId, Long id);

}
