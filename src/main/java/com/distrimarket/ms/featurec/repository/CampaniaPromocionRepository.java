package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.CampaniaPromocion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CampaniaPromocionRepository extends JpaRepository<CampaniaPromocion, Long>, JpaSpecificationExecutor<CampaniaPromocion> {

    /** Cliente que compró alguno de los productos: a quién se le envía la promoción. */
    interface Destinatario {
        Long getIdCliente();

        String getCorreo();

        String getNombre();
    }

    @EntityGraph(attributePaths = "idsProducto")
    Optional<CampaniaPromocion> findWithProductosById(Long id);

    @EntityGraph(attributePaths = "idsProducto")
    Page<CampaniaPromocion> findByActivoTrue(Pageable pageable);

    /** Promociones activas, no enviadas, cuya fecha de envío ya llegó (las toma el job). */
    @EntityGraph(attributePaths = "idsProducto")
    @Query("""
            select c from CampaniaPromocion c
            where c.activo = true and c.enviado = false
              and c.fechaEnvio is not null and c.fechaEnvio <= :ahora
            order by c.fechaEnvio
            """)
    List<CampaniaPromocion> findListasParaEnviar(@Param("ahora") LocalDateTime ahora);

    /**
     * Clientes con correo que compraron (factura emitida y activa, desde {@code desde}) alguno de
     * los productos, y a los que todavía no se les envió esta promoción. Una sola consulta.
     */
    @Query("""
            select distinct c.id as idCliente, p.correo as correo, p.nombreCompleto as nombre
            from FacturaVenta f
              join f.detalles d
              join f.cliente c
              join c.persona p
            where d.producto.id in :productos
              and f.activo = true and upper(f.estado) = 'EMITIDA'
              and f.fechaEmision >= :desde
              and c.estado = true
              and p.correo is not null and p.correo <> ''
              and not exists (
                  select 1 from CampaniaEnvio e where e.idCampania = :campania and e.idCliente = c.id)
            """)
    List<Destinatario> findDestinatariosPendientes(@Param("campania") Long campania,
                                                   @Param("productos") Collection<Long> productos,
                                                   @Param("desde") LocalDate desde,
                                                   Pageable lote);

    /** Cuántos clientes recibirían la promoción (vista previa, sin contar envíos ya hechos). */
    @Query("""
            select count(distinct c.id)
            from FacturaVenta f
              join f.detalles d
              join f.cliente c
              join c.persona p
            where d.producto.id in :productos
              and f.activo = true and upper(f.estado) = 'EMITIDA'
              and f.fechaEmision >= :desde
              and c.estado = true
              and p.correo is not null and p.correo <> ''
            """)
    long countDestinatarios(@Param("productos") Collection<Long> productos, @Param("desde") LocalDate desde);
}
