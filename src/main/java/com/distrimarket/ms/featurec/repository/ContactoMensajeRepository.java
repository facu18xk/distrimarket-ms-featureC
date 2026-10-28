package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.ContactoMensaje;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContactoMensajeRepository extends JpaRepository<ContactoMensaje, Long> {

    Page<ContactoMensaje> findByAtendido(Boolean atendido, Pageable pageable);

    /** Mensajes que el job todavía tiene que reenviar al correo de soporte (los más viejos primero). */
    @Query("""
            select m from ContactoMensaje m
            where m.correoEnviado = false and m.intentosCorreo < :maxIntentos
            order by m.id
            """)
    List<ContactoMensaje> findPendientesDeCorreo(@Param("maxIntentos") int maxIntentos, Pageable lote);
}
