package com.distrimarket.ms.featurec.config;

import com.distrimarket.commons.dto.MedioPagoDTO;
import com.distrimarket.commons.dto.MedioPagoRequestDTO;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.ms.featurec.mapper.MedioPagoMapper;
import com.distrimarket.ms.featurec.repository.MedioPagoRepository;
import com.distrimarket.ms.featurec.service.MedioPagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprueba la política de caché declarada en un servicio: lectura por id con @Cacheable,
 * actualización con @CachePut, baja con @CacheEvict e invalidación de la sección de facturas.
 * Usa una caché en memoria en lugar de Redis; las anotaciones son las mismas.
 */
@SpringJUnitConfig(CachePolicyTest.Config.class)
class CachePolicyTest {

    @Configuration
    @EnableCaching(proxyTargetClass = true) // igual que Spring Boot: proxy de la clase del servicio
    static class Config {
        @Bean
        CacheConfig.Properties cacheProperties() {
            return new CacheConfig.Properties(true, "distrimarket:ventas", Duration.ofMinutes(10), 100,
                    Map.of(CacheConfig.MEDIO_PAGO, Duration.ZERO, CacheConfig.FACTURA_VENTA, Duration.ofMinutes(10)));
        }

        @Bean
        CacheManager cacheManager(CacheConfig.Properties properties) {
            return new LoggingCache.Manager(
                    new ConcurrentMapCacheManager(CacheConfig.MEDIO_PAGO, CacheConfig.FACTURA_VENTA), properties);
        }

        @Bean
        DomainCache domainCache(CacheManager cacheManager) {
            return new DomainCache(cacheManager);
        }

        @Bean
        MedioPagoRepository medioPagoRepository() {
            return Mockito.mock(MedioPagoRepository.class);
        }

        @Bean
        MedioPagoMapper medioPagoMapper() {
            return Mockito.mock(MedioPagoMapper.class);
        }

        @Bean
        MedioPagoService medioPagoService(MedioPagoRepository repository, MedioPagoMapper mapper) {
            return new MedioPagoService(repository, mapper);
        }
    }

    @Autowired
    private MedioPagoService service;
    @Autowired
    private MedioPagoRepository repository;
    @Autowired
    private MedioPagoMapper mapper;
    @Autowired
    private CacheManager cacheManager;

    private final MedioPago efectivo = MedioPago.builder().id(7L).nombre("Efectivo").activo(true).build();

    @BeforeEach
    void setUp() {
        reset(repository, mapper);
        cacheManager.getCache(CacheConfig.MEDIO_PAGO).clear();
        cacheManager.getCache(CacheConfig.FACTURA_VENTA).clear();
        when(repository.findById(7L)).thenReturn(Optional.of(efectivo));
        when(repository.save(any(MedioPago.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toDto(any(MedioPago.class))).thenAnswer(invocation -> dto(invocation.getArgument(0)));
        when(mapper.toEntity(any(MedioPagoRequestDTO.class))).thenAnswer(invocation ->
                MedioPago.builder().nombre(((MedioPagoRequestDTO) invocation.getArgument(0)).getNombre()).build());
    }

    private static MedioPagoDTO dto(MedioPago entity) {
        MedioPagoDTO dto = new MedioPagoDTO();
        dto.setIdMedioPago(entity.getId());
        dto.setNombre(entity.getNombre());
        dto.setActivo(entity.getActivo());
        return dto;
    }

    @Test
    void laSegundaLecturaSaleDeCacheSinIrALaBase() {
        service.obtener(7L);
        MedioPagoDTO segunda = service.obtener(7L);

        assertThat(segunda.getNombre()).isEqualTo("Efectivo");
        verify(repository, times(1)).findById(7L);
    }

    @Test
    void actualizarReemplazaLaEntradaEInvalidaLasFacturas() {
        service.obtener(7L);
        cacheManager.getCache(CacheConfig.FACTURA_VENTA).put(99L, "factura que muestra el medio de pago");

        service.actualizar(7L, new MedioPagoRequestDTO("Efectivo Gs."));
        MedioPagoDTO leido = service.obtener(7L);

        assertThat(leido.getNombre()).isEqualTo("Efectivo Gs.");
        verify(repository, times(2)).findById(7L); // primera lectura + la del update; la última sale de caché
        assertThat(cacheManager.getCache(CacheConfig.FACTURA_VENTA).get(99L)).isNull();
    }

    @Test
    void darDeBajaQuitaLaEntrada() {
        service.obtener(7L);
        service.eliminar(7L);

        assertThat(cacheManager.getCache(CacheConfig.MEDIO_PAGO).get(7L)).isNull();
    }

    @Test
    void unListadoGuardaCadaElementoPeroNoLaPagina() {
        List<MedioPagoDTO> contenido = service.toResponsesCacheados(List.of(efectivo));

        assertThat(contenido).hasSize(1);
        assertThat(cacheManager.getCache(CacheConfig.MEDIO_PAGO).get(7L)).isNotNull();
        service.obtener(7L);
        verify(repository, times(0)).findById(7L);
    }
}
