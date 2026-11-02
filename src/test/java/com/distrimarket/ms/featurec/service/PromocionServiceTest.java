package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.PromocionRequestDTO;
import com.distrimarket.commons.dto.PromocionResponseDTO;
import com.distrimarket.commons.entity.CampaniaPromocion;
import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.repository.CampaniaEnvioRepository;
import com.distrimarket.ms.featurec.repository.CampaniaPromocionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromocionServiceTest {

    private final CampaniaPromocionRepository repository = mock(CampaniaPromocionRepository.class);
    private final CampaniaEnvioRepository envios = mock(CampaniaEnvioRepository.class);
    private final PromocionService service = new PromocionService(repository, envios, mock(CacheManager.class));

    private static PromocionRequestDTO pedido() {
        PromocionRequestDTO p = new PromocionRequestDTO();
        p.setTitulo(" Promo leche ");
        p.setMensaje(" 10% en lácteos ");
        p.setPorcentajeDescuento(BigDecimal.TEN);
        p.setIdsProducto(new java.util.LinkedHashSet<>(List.of(3L, 1L)));
        return p;
    }

    private static CampaniaPromocion promocion(boolean enviada) {
        CampaniaPromocion p = new CampaniaPromocion();
        p.setId(7L);
        p.setTitulo("Promo");
        p.setCuerpoHtml("Texto");
        p.setEnviado(enviada);
        p.setActivo(true);
        p.setIdsProducto(Set.of(1L));
        return p;
    }

    @Test
    void crearGuardaLosProductosYUsa180DiasPorDefecto() {
        when(repository.save(any(CampaniaPromocion.class))).thenAnswer(i -> {
            CampaniaPromocion p = i.getArgument(0);
            p.setId(7L);
            return p;
        });

        PromocionResponseDTO dto = service.crear(pedido());

        assertThat(dto.getTitulo()).isEqualTo("Promo leche");
        assertThat(dto.getIdsProducto()).containsExactly(1L, 3L);
        assertThat(dto.getDiasHistorial()).isEqualTo(180);
        assertThat(dto.getEnviado()).isFalse();
    }

    @Test
    void fechaFinAnteriorAInicioSeRechaza() {
        PromocionRequestDTO p = pedido();
        p.setFechaInicio(LocalDate.of(2026, 11, 10));
        p.setFechaFin(LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> service.crear(p)).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void unaPromocionEnviadaNoSeModificaNiSeReenvia() {
        when(repository.findWithProductosById(7L)).thenReturn(Optional.of(promocion(true)));

        assertThatThrownBy(() -> service.actualizar(7L, pedido())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.enviarAhora(7L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void enviarAhoraProgramaElEnvio() {
        CampaniaPromocion p = promocion(false);
        when(repository.findWithProductosById(7L)).thenReturn(Optional.of(p));
        when(repository.save(p)).thenReturn(p);

        service.enviarAhora(7L);

        assertThat(p.getFechaEnvio()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void eliminarEsBajaLogicaYLuegoNoSeEncuentra() {
        CampaniaPromocion p = promocion(false);
        when(repository.findWithProductosById(7L)).thenReturn(Optional.of(p));

        service.eliminar(7L);

        assertThat(p.getActivo()).isFalse();
        assertThatThrownBy(() -> service.obtener(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void destinatariosCuentaLosClientesQueCompraronEsosProductos() {
        CampaniaPromocion p = promocion(false);
        p.setDiasHistorial(30);
        when(repository.findWithProductosById(7L)).thenReturn(Optional.of(p));
        when(repository.countDestinatarios(eq(Set.of(1L)), eq(LocalDate.now().minusDays(30)))).thenReturn(4L);

        assertThat(service.destinatarios(7L).getClientes()).isEqualTo(4);
    }

    @Test
    void elDescuentoVigenteMayorGanaYElPrecioSeRedondeaEnGuaranies() {
        CampaniaPromocion diez = promocion(false);
        diez.setPorcentajeDescuento(BigDecimal.TEN);
        diez.setIdsProducto(Set.of(1L, 2L));
        CampaniaPromocion veinte = promocion(false);
        veinte.setId(8L);
        veinte.setPorcentajeDescuento(new BigDecimal("20"));
        veinte.setIdsProducto(Set.of(2L));
        when(repository.findConDescuentoVigente(LocalDate.of(2026, 10, 31))).thenReturn(List.of(diez, veinte));

        var descuentos = service.descuentosVigentes(LocalDate.of(2026, 10, 31));

        assertThat(descuentos.get(1L).porcentaje()).isEqualByComparingTo("10");
        assertThat(descuentos.get(2L).porcentaje()).isEqualByComparingTo("20");
        assertThat(descuentos).doesNotContainKey(3L);
        assertThat(PromocionService.conDescuento(new BigDecimal("6000"), new BigDecimal("10"))).isEqualByComparingTo("5400");
        assertThat(PromocionService.conDescuento(new BigDecimal("6005"), new BigDecimal("12.5"))).isEqualByComparingTo("5254");
        assertThat(PromocionService.conDescuento(new BigDecimal("6000"), null)).isEqualByComparingTo("6000");
    }

    @Test
    void listarSinOrdenUsaIdDescendente() {
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<CampaniaPromocion>>any(), any(Pageable.class))).thenReturn(Page.empty());

        service.listar(null, PageRequest.of(0, 10));

        Pageable orden = capturarOrdenDeListado();
        assertThat(orden.getSort().toList()).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    void listarConservaElOrdenPedidoYAgregaIdComoDesempate() {
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<CampaniaPromocion>>any(), any(Pageable.class))).thenReturn(Page.empty());

        service.listar(null, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "titulo")));

        Pageable orden = capturarOrdenDeListado();
        assertThat(orden.getSort().stream().map(Sort.Order::getProperty)).containsExactly("titulo", "id");
        assertThat(orden.getSort().getOrderFor("titulo").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(orden.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listarAceptaLasClavesDeOrdenDeLaUi() {
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<CampaniaPromocion>>any(), any(Pageable.class))).thenReturn(Page.empty());

        assertThatCode(() -> {
            for (String propiedad : List.of("titulo", "porcentajeDescuento", "fechaInicio", "fechaFin",
                    "fechaEnvio", "enviado", "fechaCreacion", "fechaModificacion")) {
                service.listar(null, PageRequest.of(0, 10, Sort.by(propiedad)));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void listarRechazaPropiedadesDeOrdenNoPermitidas() {
        assertThatThrownBy(() -> service.listar(null, PageRequest.of(0, 10, Sort.by("idsProducto"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idsProducto")
                .hasMessageContaining("Valores permitidos");
        verify(repository, never()).findAll(org.mockito.ArgumentMatchers.<Specification<CampaniaPromocion>>any(), any(Pageable.class));
    }

    private Pageable capturarOrdenDeListado() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(org.mockito.ArgumentMatchers.<Specification<CampaniaPromocion>>any(), captor.capture());
        return captor.getValue();
    }
}
