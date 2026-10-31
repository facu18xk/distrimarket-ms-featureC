package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.FacturaVenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Un producto en el reporte de ventas: unidades, monto (IVA incluido) y en cuántas facturas aparece. */
    interface ProductoVendido {
        Long getIdProducto();

        Long getUnidades();

        BigDecimal getTotal();

        Long getFacturas();
    }

    /**
     * Productos más vendidos (por unidades) en facturas emitidas y activas del período, con los
     * mismos filtros opcionales del reporte. Agrupa las líneas en la base, en una sola consulta.
     */
    @Query("""
            select d.producto.id as idProducto, sum(d.cantidad) as unidades,
                   sum(d.subtotal) as total, count(distinct f.id) as facturas
            from FacturaVentaDetalle d
              join d.facturaVenta f
            where f.activo = true and upper(f.estado) = 'EMITIDA'
              and f.fechaEmision >= :desde and f.fechaEmision <= :hasta
              and (:idCliente is null or f.cliente.id = :idCliente)
              and (:idEmpleado is null or f.empleado.id = :idEmpleado)
              and (:idDeposito is null or f.deposito.id = :idDeposito)
            group by d.producto.id
            order by sum(d.cantidad) desc, sum(d.subtotal) desc
            """)
    List<ProductoVendido> productosMasVendidos(@Param("desde") LocalDate desde,
                                               @Param("hasta") LocalDate hasta,
                                               @Param("idCliente") Long idCliente,
                                               @Param("idEmpleado") Long idEmpleado,
                                               @Param("idDeposito") Long idDeposito);


    /**
     * Último número de factura de un timbrado con el prefijo dado (001-001-%). Incluye anuladas y
     * dadas de baja: el número no se puede repetir dentro del timbrado. Como la secuencia tiene
     * 7 dígitos con ceros a la izquierda, el mayor como texto es también el mayor como número.
     */
    @Query("""
            select max(f.numeroFactura) from FacturaVenta f
            where f.timbrado.id = :idTimbrado and f.numeroFactura like :prefijo
            """)
    Optional<String> ultimoNumero(@Param("idTimbrado") Long idTimbrado, @Param("prefijo") String prefijo);

}
