package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.PromocionDestinatariosDTO;
import com.distrimarket.commons.dto.PromocionRequestDTO;
import com.distrimarket.commons.dto.PromocionResponseDTO;
import com.distrimarket.commons.entity.CampaniaPromocion;
import com.distrimarket.commons.service.BusquedaTexto;
import com.distrimarket.commons.service.CacheEnLote;
import com.distrimarket.commons.service.Paginas;
import com.distrimarket.ms.featurec.config.CacheConfig;
import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.repository.CampaniaEnvioRepository;
import com.distrimarket.ms.featurec.repository.CampaniaPromocionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Promociones por producto. El envío por correo lo hace el job {@code PromocionesJob}: toma las
 * promociones cuya fecha de envío llegó y escribe a los clientes que compraron esos productos.
 */
@Slf4j
@Service
public class PromocionService {

    private final CampaniaPromocionRepository repository;
    private final CampaniaEnvioRepository envioRepository;
    private final CacheManager cacheManager;

    public PromocionService(CampaniaPromocionRepository repository, CampaniaEnvioRepository envioRepository, CacheManager cacheManager) {
        this.repository = repository;
        this.envioRepository = envioRepository;
        this.cacheManager = cacheManager;
    }

    /**
     * Listado: de la base, la página de ids (filtro por título y solo activas); cada promoción sale
     * de la caché por id y las que faltan se traen en una consulta (ver CacheEnLote).
     */
    @Transactional(readOnly = true)
    public Page<PromocionResponseDTO> listar(String q, Pageable pageable) {
        Specification<CampaniaPromocion> filtro = Specification.<CampaniaPromocion>where((root, query, builder) -> builder.isTrue(root.get("activo")))
                .and(BusquedaTexto.en(q, "titulo"));
        // Pocas filas y livianas: la página ya trae las promociones; las que no estaban en caché se
        // arman desde ahí (sin otra consulta) y se guardan.
        Page<CampaniaPromocion> pagina = repository.findAll(filtro, Paginas.conDesempatePorId(pageable));
        var porId = new java.util.HashMap<Long, CampaniaPromocion>();
        pagina.forEach(p -> porId.put(p.getId(), p));
        return CacheEnLote.pagina(
                new PageImpl<>(new ArrayList<>(pagina.getContent().stream().map(CampaniaPromocion::getId).toList()), pagina.getPageable(), pagina.getTotalElements()),
                cacheManager.getCache(CacheConfig.PROMOCION),
                faltan -> faltan.stream().map(porId::get).map(this::toDto).toList(),
                PromocionResponseDTO::getId);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PROMOCION, key = "#id")
    public PromocionResponseDTO obtener(Long id) {
        return toDto(buscar(id));
    }

    @Transactional
    @CachePut(cacheNames = CacheConfig.PROMOCION, key = "#result.id")
    public PromocionResponseDTO crear(PromocionRequestDTO request) {
        CampaniaPromocion promocion = new CampaniaPromocion();
        copiar(request, promocion);
        CampaniaPromocion guardada = repository.save(promocion);
        log.info("Promoción {} creada: '{}' sobre {} producto(s)", guardada.getId(), guardada.getTitulo(), guardada.getIdsProducto().size());
        return toDto(guardada);
    }

    @Transactional
    @CachePut(cacheNames = CacheConfig.PROMOCION, key = "#id")
    public PromocionResponseDTO actualizar(Long id, PromocionRequestDTO request) {
        CampaniaPromocion promocion = buscar(id);
        if (Boolean.TRUE.equals(promocion.getEnviado())) {
            throw new ConflictException("La promoción ya se envió: no se puede modificar.");
        }
        copiar(request, promocion);
        return toDto(repository.save(promocion));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PROMOCION, key = "#id")
    public void eliminar(Long id) {
        CampaniaPromocion promocion = buscar(id);
        promocion.setActivo(false);
        repository.save(promocion);
        log.info("Promoción {} dada de baja", id);
    }

    /** Programa el envío para ya: el job la envía en su próxima ejecución. */
    @Transactional
    @CachePut(cacheNames = CacheConfig.PROMOCION, key = "#id")
    public PromocionResponseDTO enviarAhora(Long id) {
        CampaniaPromocion promocion = buscar(id);
        if (Boolean.TRUE.equals(promocion.getEnviado())) {
            throw new ConflictException("La promoción ya se envió.");
        }
        promocion.setFechaEnvio(LocalDateTime.now());
        log.info("Promoción {} programada para enviarse ahora", id);
        return toDto(repository.save(promocion));
    }

    @Transactional(readOnly = true)
    public PromocionDestinatariosDTO destinatarios(Long id) {
        CampaniaPromocion promocion = buscar(id);
        PromocionDestinatariosDTO dto = new PromocionDestinatariosDTO();
        dto.setClientes((int) repository.countDestinatarios(promocion.getIdsProducto(), desde(promocion)));
        return dto;
    }

    /** Fecha desde la que cuentan las compras de los productos. */
    public static LocalDate desde(CampaniaPromocion promocion) {
        return LocalDate.now().minusDays(promocion.getDiasHistorial() == null ? 180 : promocion.getDiasHistorial());
    }

    private CampaniaPromocion buscar(Long id) {
        return repository.findWithProductosById(id)
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Promoción", id));
    }

    private void copiar(PromocionRequestDTO request, CampaniaPromocion promocion) {
        if (request.getFechaInicio() != null && request.getFechaFin() != null && request.getFechaFin().isBefore(request.getFechaInicio())) {
            throw new BusinessRuleException("La fecha de fin no puede ser anterior a la de inicio.");
        }
        promocion.setTitulo(request.getTitulo().trim());
        promocion.setCuerpoHtml(request.getMensaje().trim());
        promocion.setPorcentajeDescuento(request.getPorcentajeDescuento());
        promocion.setFechaInicio(request.getFechaInicio());
        promocion.setFechaFin(request.getFechaFin());
        promocion.setDiasHistorial(request.getDiasHistorial() == null ? 180 : request.getDiasHistorial());
        promocion.setFechaEnvio(request.getFechaEnvio() == null ? null : request.getFechaEnvio().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        promocion.setIdsProducto(new HashSet<>(request.getIdsProducto()));
    }

    PromocionResponseDTO toDto(CampaniaPromocion p) {
        PromocionResponseDTO dto = new PromocionResponseDTO();
        dto.setId(p.getId());
        dto.setTitulo(p.getTitulo());
        dto.setMensaje(p.getCuerpoHtml());
        dto.setPorcentajeDescuento(p.getPorcentajeDescuento());
        dto.setFechaInicio(p.getFechaInicio());
        dto.setFechaFin(p.getFechaFin());
        dto.setDiasHistorial(p.getDiasHistorial());
        dto.setFechaEnvio(p.getFechaEnvio() == null ? null : p.getFechaEnvio().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        dto.setEnviado(p.getEnviado());
        dto.setActivo(p.getActivo());
        dto.setIdsProducto(new ArrayList<>(p.getIdsProducto()).stream().sorted().toList());
        dto.setCorreosEnviados((int) envioRepository.countByIdCampaniaAndEnviadoTrue(p.getId()));
        return dto;
    }
}
