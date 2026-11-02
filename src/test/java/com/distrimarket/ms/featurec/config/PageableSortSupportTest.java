package com.distrimarket.ms.featurec.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageableSortSupportTest {

    @Test
    void sinOrdenUsaIdDescendente() {
        Pageable resultado = PageableSortSupport.allowSorts(PageRequest.of(0, 10), "id", "fechaCreacion");

        assertThat(resultado.getSort().toList()).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    void conservaElOrdenPermitidoYAgregaIdComoDesempate() {
        Pageable resultado = PageableSortSupport.allowSorts(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fechaCreacion")),
                "id", "fechaCreacion");

        assertThat(resultado.getSort().stream().map(Sort.Order::getProperty))
                .containsExactly("fechaCreacion", "id");
        assertThat(resultado.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void rechazaPropiedadesNoPermitidasEnLugarDeIgnorarlas() {
        assertThatThrownBy(() -> PageableSortSupport.allowSorts(
                PageRequest.of(0, 10, Sort.by("desconocido")), "id", "fechaCreacion"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desconocido")
                .hasMessageContaining("Valores permitidos");
    }
}
