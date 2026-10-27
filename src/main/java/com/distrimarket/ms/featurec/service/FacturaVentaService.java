package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.commons.entity.FacturaVentaDetalle;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaResumenDTO;
import com.distrimarket.commons.entity.BaseEntity;
import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Deposito;
import com.distrimarket.commons.entity.Empleado;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.commons.entity.Producto;
import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.commons.dto.DepositoResponseDTO;
import com.distrimarket.commons.dto.EstadoFacturaVenta;
import com.distrimarket.commons.dto.ProductoResponseDTO;
import com.distrimarket.ms.featurec.config.CacheConfig;
import com.distrimarket.ms.featurec.config.PageableSortSupport;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import com.distrimarket.ms.featurec.config.SearchQuerySupport;
import com.distrimarket.commons.dto.FacturaVentaRequestDTO;
import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import com.distrimarket.ms.featurec.repository.FacturaVentaRepository;
import com.distrimarket.ms.featurec.repository.MedioPagoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class FacturaVentaService
        extends AbstractBaseService<
        FacturaVenta, FacturaVentaRequestDTO, FacturaVentaResponseDTO,
        FacturaVentaRepository, FacturaVentaMapper> {

    private final FacturaVentaRepository repository;
    private final MedioPagoRepository medioPagoRepository;
    private final InventarioService inventarioService;
    private final EntityManager entityManager;

    public FacturaVentaService(
            FacturaVentaRepository repository,
            FacturaVentaMapper mapper,
            MedioPagoRepository medioPagoRepository,
            InventarioService inventarioService,
            EntityManager entityManager) {
        super(repository, mapper, "Factura de venta");
        this.repository = repository;
        this.medioPagoRepository = medioPagoRepository;
        this.inventarioService = inventarioService;
        this.entityManager = entityManager;
    }

    /**
     * Cabeceras para un listado. No llevan líneas ni datos de inventario: el detalle de una
     * factura se pide por id cuando se abre (y queda en caché).
     */
    public List<FacturaVentaResumenDTO> toResumenes(List<FacturaVenta> facturas) {
        return facturas.stream().map(mapper::toResumen).toList();
    }

    // --- Caché (dominio "factura-venta"): la factura completa, por id ---

    @Override
    @Cacheable(cacheNames = CacheConfig.FACTURA_VENTA, key = "#id")
    public FacturaVentaResponseDTO obtener(Long id) {
        return super.obtener(id);
    }

    @Override
    @CachePut(cacheNames = CacheConfig.FACTURA_VENTA, key = "#result.idFacturaVenta")
    public FacturaVentaResponseDTO crear(FacturaVentaRequestDTO request) {
        return super.crear(request);
    }

    @Override
    @CachePut(cacheNames = CacheConfig.FACTURA_VENTA, key = "#id")
    public FacturaVentaResponseDTO actualizar(Long id, FacturaVentaRequestDTO request) {
        return super.actualizar(id, request);
    }

    /** Anular o volver a emitir: la factura en caché se reemplaza por la nueva versión. */
    @CachePut(cacheNames = CacheConfig.FACTURA_VENTA, key = "#id")
    @Transactional
    public FacturaVentaResponseDTO cambiarEstado(Long id, EstadoFacturaVenta estado) {
        return toResponse(actualizarEstado(id, estado));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.FACTURA_VENTA, key = "#id")
    public void eliminar(Long id) {
        super.eliminar(id);
    }

    private void validarEditable(FacturaVenta factura) {
        if ("ANULADA".equalsIgnoreCase(factura.getEstado())) {
            log.warn("Intento de modificar la factura anulada {}", factura.getId());
            throw new ConflictException("No se puede modificar una factura anulada.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FacturaVenta get(Long id) {
        validarId(id);
        FacturaVenta factura = repository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura de venta", id));
        return validarActivo(factura, id);
    }

    @Override
    @Transactional
    public FacturaVenta create(FacturaVenta entity) {
        validar(entity);
        entity.setId(null);
        if (entity.getActivo() == null) {
            entity.setActivo(true);
        }
        resolverReferencias(entity);
        validarNumeroUnico(entity, null);
        prepararDetalles(entity);
        ajustarStock(stockChanges(entity, 1), "Venta factura " + entity.getNumeroFactura());
        entity.recalcularTotales();
        log.debug("Totales de la factura {}: iva={} general={}",
                entity.getNumeroFactura(), entity.getTotalIva(), entity.getTotalGeneral());
        log.info("Emitiendo factura de venta {}", entity.getNumeroFactura());
        FacturaVenta guardada = repository.saveAndFlush(entity);
        return repository.findDetailedById(guardada.getId()).orElse(guardada);
    }

    @Override
    @Transactional
    public FacturaVenta update(Long id, FacturaVenta changes) {
        FacturaVenta invoice = get(id);
        copiarCambios(invoice, changes);
        validar(invoice);
        log.info("Actualizando Factura de venta {}", id);
        repository.saveAndFlush(invoice);
        return repository.findDetailedById(id).orElse(invoice);
    }

    @Transactional
    public FacturaVenta actualizarEstado(Long id, EstadoFacturaVenta estado) {
        if (estado == null) {
            throw new IllegalArgumentException("El estado de la factura es obligatorio.");
        }
        FacturaVenta factura = get(id);
        if (estado.name().equalsIgnoreCase(factura.getEstado())) {
            log.warn("Intento de cambiar la factura {} al estado que ya tiene: {}", id, estado);
            throw new ConflictException("La factura ya se encuentra en estado " + estado.name() + ".");
        }
        if (EstadoFacturaVenta.EMITIDA.name().equals(factura.getEstado())
                && estado == EstadoFacturaVenta.ANULADA) {
            reponerStock(factura, "Anulación de factura " + factura.getNumeroFactura());
        } else if (EstadoFacturaVenta.ANULADA.name().equals(factura.getEstado())
                && estado == EstadoFacturaVenta.EMITIDA) {
            ajustarStock(stockChanges(factura, 1), "Reemisión de factura " + factura.getNumeroFactura());
        }
        factura.setEstado(estado.name());
        log.info("Actualizando estado de factura {} a {}", factura.getNumeroFactura(), estado);
        return repository.save(factura);
    }

    @Transactional(readOnly = true)
    public Page<FacturaVenta> search(
            String query,
            Pageable pageable) {
        return search(query, null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FacturaVenta> search(
            String query,
            Long idCliente,
            Long idEmpleado,
            Long idDeposito,
            EstadoFacturaVenta estado,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Pageable pageable) {
        if (fechaDesde != null && fechaHasta != null && fechaHasta.isBefore(fechaDesde)) {
            throw new IllegalArgumentException("fechaHasta no puede ser anterior a fechaDesde.");
        }
        Specification<FacturaVenta> specification = (root, criteriaQuery, builder) -> {
            var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (idCliente != null) {
                filters.add(builder.equal(root.get("cliente").get("id"), idCliente));
            }
            if (idEmpleado != null) {
                filters.add(builder.equal(root.get("empleado").get("id"), idEmpleado));
            }
            if (idDeposito != null) {
                filters.add(builder.equal(root.get("deposito").get("id"), idDeposito));
            }
            if (estado != null) {
                filters.add(builder.equal(root.get("estado"), estado.name()));
            }
            if (fechaDesde != null) {
                filters.add(builder.greaterThanOrEqualTo(root.get("fechaEmision"), fechaDesde));
            }
            if (fechaHasta != null) {
                filters.add(builder.lessThanOrEqualTo(root.get("fechaEmision"), fechaHasta));
            }
            return filters.isEmpty()
                    ? builder.conjunction()
                    : builder.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, criteriaQuery, builder) -> {
                var matches = new ArrayList<jakarta.persistence.criteria.Predicate>();
                matches.add(builder.like(builder.lower(root.get("numeroFactura")), pattern));
                matches.add(builder.like(builder.lower(root.get("estado")), pattern));
                matches.add(builder.like(builder.lower(root.get("cliente").get("persona")
                        .get("nombreCompleto")), pattern));

                var searchedDate = SearchQuerySupport.parseDate(query);
                String digits = SearchQuerySupport.digitsOnly(query);
                if (searchedDate.isEmpty() && !digits.isEmpty()) {
                    var normalizedNumber = builder.function(
                            "regexp_replace",
                            String.class,
                            root.get("numeroFactura"),
                            builder.literal("[^0-9]"),
                            builder.literal(""),
                            builder.literal("g"));
                    matches.add(builder.like(normalizedNumber, "%" + digits + "%"));
                }

                searchedDate.ifPresent(date -> {
                    matches.add(builder.equal(root.get("fechaEmision"), date));
                    LocalDateTime start = date.atStartOfDay();
                    LocalDateTime end = date.plusDays(1).atStartOfDay();
                    matches.add(builder.and(
                            builder.greaterThanOrEqualTo(root.get("fechaCreacion"), start),
                            builder.lessThan(root.get("fechaCreacion"), end)));
                    matches.add(builder.and(
                            builder.greaterThanOrEqualTo(root.get("fechaModificacion"), start),
                            builder.lessThan(root.get("fechaModificacion"), end)));
                });
                try {
                    matches.add(builder.equal(root.get("id"), Long.valueOf(query.trim())));
                } catch (NumberFormatException ignored) {
                    // The query is not a numeric invoice ID.
                }
                return builder.or(matches.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
        }
        Pageable sortedPageable = PageableSortSupport.allowSorts(pageable,
                "id", "fechaCreacion", "fechaModificacion",
                "numeroFactura", "fechaEmision", "estado", "totalIva", "totalGeneral");
        // Solo cabeceras (con cliente, medio de pago y timbrado por EntityGraph): las líneas
        // no se cargan en un listado.
        return search(specification, sortedPageable);
    }

    @Override
    protected void copiarCambios(FacturaVenta target, FacturaVenta source) {
        if (source != null && source.getFechaEmision() == null) {
            source.setFechaEmision(target.getFechaEmision());
        }
        validar(source);
        validarEditable(target);
        resolverReferencias(source);
        validarNumeroUnico(source, target.getId());
        prepararDetalles(source);
        Map<StockKey, Integer> stockChanges = stockChanges(source, 1);
        restarStockActual(stockChanges, target);
        ajustarStock(stockChanges, "Modificación de factura " + source.getNumeroFactura());

        Map<Long, Deque<FacturaVentaDetalle>> existingByProduct = new HashMap<>();
        for (FacturaVentaDetalle detalle : target.getDetalles()) {
            existingByProduct.computeIfAbsent(detalle.getProducto().getId(), ignored -> new ArrayDeque<>())
                    .addLast(detalle);
        }
        Set<FacturaVentaDetalle> retainedDetails =
                Collections.newSetFromMap(new IdentityHashMap<>());
        var newDetails = new ArrayList<FacturaVentaDetalle>();
        for (FacturaVentaDetalle requested : source.getDetalles()) {
            Deque<FacturaVentaDetalle> candidates = existingByProduct.get(requested.getProducto().getId());
            FacturaVentaDetalle current = candidates == null ? null : candidates.pollFirst();
            if (current == null) {
                newDetails.add(requested);
                continue;
            }
            // Las líneas ya facturadas conservan el precio e IVA con los que se emitieron.
            current.setCantidad(requested.getCantidad());
            current.calcularSubtotal();
            retainedDetails.add(current);
        }
        target.getDetalles().removeIf(detalle -> !retainedDetails.contains(detalle));
        for (FacturaVentaDetalle detail : newDetails) {
            target.agregarDetalle(detail);
        }

        target.setDeposito(source.getDeposito());
        target.setMedioPago(source.getMedioPago());
        target.setNumeroFactura(source.getNumeroFactura());
        target.setFechaEmision(source.getFechaEmision());
        target.setCliente(source.getCliente());
        target.setEmpleado(source.getEmpleado());
        target.setTimbrado(source.getTimbrado());
        target.recalcularTotales();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        FacturaVenta factura = get(id);
        if ("EMITIDA".equalsIgnoreCase(factura.getEstado())) {
            reponerStock(factura, "Baja de factura " + factura.getNumeroFactura());
            factura.setEstado(EstadoFacturaVenta.ANULADA.name());
        }
        factura.setActivo(false);
        repository.save(factura);
        log.info("Factura de venta {} marcada como inactiva; sus detalles se conservan", id);
    }

    @Override
    protected void marcarInactivo(FacturaVenta entity) {
        entity.setActivo(false);
    }

    @Override
    protected void validar(FacturaVenta entity) {
        super.validar(entity);
        if (entity.getCliente() == null || entity.getEmpleado() == null
                || entity.getDeposito() == null || entity.getMedioPago() == null
                || entity.getTimbrado() == null) {
            throw new IllegalArgumentException("Cliente, empleado, depósito, medio de pago y timbrado son obligatorios.");
        }
        if (entity.getNumeroFactura() == null || entity.getNumeroFactura().isBlank()) {
            throw new IllegalArgumentException("El número de factura es obligatorio.");
        }
        String numeroFactura = entity.getNumeroFactura().trim();
        if (numeroFactura.length() > 50) {
            throw new IllegalArgumentException("El número de factura no puede superar 50 caracteres.");
        }
        entity.setNumeroFactura(numeroFactura);
        if (entity.getFechaEmision() == null) {
            entity.setFechaEmision(LocalDate.now());
        }
        if (entity.getDetalles() == null || entity.getDetalles().isEmpty()) {
            throw new BusinessRuleException("La factura debe contener al menos un detalle.");
        }
        if (entity.getEstado() == null) {
            entity.setEstado(EstadoFacturaVenta.EMITIDA.name());
        } else {
            String estado = entity.getEstado().trim().toUpperCase(Locale.ROOT);
            if (!EstadoFacturaVenta.EMITIDA.name().equals(estado)) {
                throw new IllegalArgumentException(
                        "Una factura debe emitirse como EMITIDA; para anularla, use la operación de estado.");
            }
            entity.setEstado(estado);
        }
    }

    private void validarNumeroUnico(FacturaVenta factura, Long idActual) {
        Long timbradoId = factura.getTimbrado().getId();
        boolean duplicada = idActual == null
                ? repository.existsByNumeroFacturaAndTimbrado_Id(factura.getNumeroFactura(), timbradoId)
                : repository.existsByNumeroFacturaAndTimbrado_IdAndIdNot(
                        factura.getNumeroFactura(), timbradoId, idActual);
        if (duplicada) {
            log.warn("Intento de registrar un número de factura duplicado: {} (timbrado {})",
                    factura.getNumeroFactura(), timbradoId);
            throw new ConflictException(
                    "Ya existe una factura con el número %s para el timbrado indicado."
                            .formatted(factura.getNumeroFactura()));
        }
    }

    private void prepararDetalles(FacturaVenta entity) {
        // Caché local por operación para no repetir llamadas HTTP Feign a inventario
        // cuando la misma factura incluye varias líneas del mismo producto.
        Map<Long, ProductoResponseDTO> productosPorId = new HashMap<>();
        for (FacturaVentaDetalle detalle : entity.getDetalles()) {
            prepararDetalle(detalle, productosPorId);
            detalle.setFacturaVenta(entity);
        }
    }

    private void prepararDetalle(FacturaVentaDetalle detalle, Map<Long, ProductoResponseDTO> productosPorId) {
        if (detalle == null || detalle.getProducto() == null || detalle.getProducto().getId() == null) {
            throw new IllegalArgumentException("Cada detalle debe indicar un producto.");
        }
        Long productoId = detalle.getProducto().getId();
        // El producto es de inventario: de allí salen precio, IVA y estado; aquí solo se guarda la referencia.
        ProductoResponseDTO producto = productosPorId.computeIfAbsent(
                productoId, inventarioService::obtenerProducto);
        log.debug("Producto {} recuperado: precio={} iva={}",
                productoId, producto.getPrecioVenta(), producto.getPorcentajeIva());
        if (!Boolean.TRUE.equals(producto.getEstado())) {
            log.warn("Intento de vender el producto inactivo {}", productoId);
            throw new BusinessRuleException("El producto " + productoId + " está inactivo.");
        }
        detalle.setProducto(entityManager.getReference(Producto.class, productoId));
        detalle.setPrecioUnitario(producto.getPrecioVenta());
        detalle.setPorcentajeIva(producto.getPorcentajeIva());
        validarDetalle(detalle);
    }

    private void resolverReferencias(FacturaVenta entity) {
        entity.setCliente(resolver(Cliente.class, entity.getCliente(), "Cliente"));
        // Empleado y depósito son de inventario: se validan allí y aquí solo se guarda la referencia.
        if (entity.getEmpleado() == null || entity.getEmpleado().getId() == null) {
            throw new IllegalArgumentException("Empleado es obligatorio.");
        }
        Long empleadoId = entity.getEmpleado().getId();
        if (!Boolean.TRUE.equals(inventarioService.obtenerEmpleado(empleadoId).getEstado())) {
            throw new BusinessRuleException("El empleado está inactivo.");
        }
        entity.setEmpleado(entityManager.getReference(Empleado.class, empleadoId));
        if (entity.getDeposito() == null || entity.getDeposito().getId() == null) {
            throw new IllegalArgumentException("Depósito es obligatorio.");
        }
        Long depositoId = entity.getDeposito().getId();
        DepositoResponseDTO deposito = inventarioService.obtenerDeposito(depositoId);
        if (!Boolean.TRUE.equals(deposito.getEstado())) {
            throw new BusinessRuleException("El depósito está inactivo.");
        }
        entity.setDeposito(entityManager.getReference(Deposito.class, depositoId));
        MedioPago medioPago = entity.getMedioPago();
        if (medioPago == null || medioPago.getId() == null) {
            throw new IllegalArgumentException("Medio de pago es obligatorio.");
        }
        entity.setMedioPago(medioPagoRepository.findById(medioPago.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medio de pago", medioPago.getId())));
        entity.setTimbrado(resolver(Timbrado.class, entity.getTimbrado(), "Timbrado"));
        if (!Boolean.TRUE.equals(entity.getCliente().getEstado())) {
            throw new BusinessRuleException("El cliente está inactivo.");
        }
        if (!Boolean.TRUE.equals(entity.getMedioPago().getActivo())) {
            throw new BusinessRuleException("El medio de pago está inactivo.");
        }
        if (!Boolean.TRUE.equals(entity.getTimbrado().getActivo())) {
            throw new BusinessRuleException("El timbrado está inactivo.");
        }
        if (entity.getTimbrado().getFechaInicio() != null
                && entity.getFechaEmision().isBefore(entity.getTimbrado().getFechaInicio())) {
            throw new BusinessRuleException("La fecha de emisión es anterior a la vigencia del timbrado.");
        }
        if (entity.getTimbrado().getFechaVencimiento() != null
                && entity.getFechaEmision().isAfter(entity.getTimbrado().getFechaVencimiento())) {
            throw new BusinessRuleException("El timbrado está vencido para la fecha de emisión indicada.");
        }
    }

    private <E extends BaseEntity> E resolver(Class<E> type, E reference, String resourceName) {
        if (reference == null || reference.getId() == null) {
            throw new IllegalArgumentException(resourceName + " es obligatorio.");
        }
        Long id = reference.getId();
        E resolved = entityManager.find(type, id);
        if (resolved == null) {
            throw new ResourceNotFoundException(resourceName, id);
        }
        return resolved;
    }

    private void validarDetalle(FacturaVentaDetalle detalle) {
        if (detalle == null) {
            throw new IllegalArgumentException("La factura no puede contener detalles nulos.");
        }
        if (detalle.getProducto() == null) {
            throw new IllegalArgumentException("Cada detalle debe tener un producto.");
        }
        if (detalle.getCantidad() == null || detalle.getCantidad() <= 0) {
            throw new IllegalArgumentException("La cantidad de cada detalle debe ser mayor que cero.");
        }
        if (detalle.getPrecioUnitario() == null || detalle.getPrecioUnitario().signum() < 0) {
            throw new IllegalArgumentException("El precio unitario de cada detalle no puede ser negativo.");
        }
        if (detalle.getPorcentajeIva() == null || detalle.getPorcentajeIva().signum() < 0) {
            throw new IllegalArgumentException("El porcentaje de IVA de cada detalle no puede ser negativo.");
        }
        detalle.calcularSubtotal();
    }

    private Map<StockKey, Integer> stockChanges(FacturaVenta factura, int multiplier) {
        Map<StockKey, Integer> changes = new HashMap<>();
        if (factura.getDetalles() != null && !factura.getDetalles().isEmpty()) {
            Long depositoId = factura.getDeposito().getId();
            for (FacturaVentaDetalle detalle : factura.getDetalles()) {
                acumular(changes, new StockKey(depositoId, detalle.getProducto().getId()),
                        multiplier * detalle.getCantidad());
            }
        }
        return changes;
    }

    private void restarStockActual(Map<StockKey, Integer> changes, FacturaVenta factura) {
        if (factura.getDetalles() == null) {
            return;
        }
        Long depositoId = factura.getDeposito().getId();
        for (FacturaVentaDetalle detalle : factura.getDetalles()) {
            acumular(changes, new StockKey(depositoId, detalle.getProducto().getId()),
                    -detalle.getCantidad());
        }
    }

    /**
     * Devuelve a inventario lo que la factura había consumido. Anular o dar de baja una venta siempre
     * debe poder hacerse: si inventario dio de baja el depósito o algún producto, eso no se repone
     * (ya no hay stock que llevar) y queda registrado.
     */
    private void reponerStock(FacturaVenta factura, String motivo) {
        Map<StockKey, Integer> changes = stockChanges(factura, -1);
        if (changes.isEmpty()) {
            return;
        }
        Long depositoId = factura.getDeposito().getId();
        if (inventarioService.buscarDeposito(depositoId).isEmpty()) {
            log.warn("El depósito {} ya no está en inventario; no se repone stock ({})", depositoId, motivo);
            return;
        }
        changes.keySet().removeIf(key -> {
            boolean dadoDeBaja = inventarioService.buscarProducto(key.productoId()).isEmpty();
            if (dadoDeBaja) {
                log.warn("El producto {} ya no está en inventario; su stock no se repone ({})",
                        key.productoId(), motivo);
            }
            return dadoDeBaja;
        });
        ajustarStock(changes, motivo);
    }

    /**
     * Pide a inventario que aplique los cambios de stock. En {@code changes} una cantidad positiva
     * es lo que la factura consume; inventario espera el signo contrario (negativo descuenta).
     */
    private void ajustarStock(Map<StockKey, Integer> changes, String motivo) {
        Map<Long, Map<Long, Integer>> movimientosPorDeposito = new TreeMap<>();
        changes.forEach((key, consumo) -> {
            if (consumo != 0) {
                movimientosPorDeposito.computeIfAbsent(key.depositoId(), ignored -> new TreeMap<>())
                        .put(key.productoId(), Math.negateExact(consumo));
            }
        });

        movimientosPorDeposito.forEach((depositoId, movimientos) -> {
            log.debug("Movimiento de stock para el depósito {}: {} ({})", depositoId, movimientos, motivo);
            inventarioService.moverStock(depositoId, movimientos, motivo);
            revertirStockSiLaTransaccionFalla(depositoId, movimientos, motivo);
        });
    }

    /**
     * El stock vive en otro microservicio y no participa de la transacción de ventas: si la factura
     * no llega a guardarse, se devuelve a inventario lo que ya se había movido.
     */
    private void revertirStockSiLaTransaccionFalla(Long depositoId, Map<Long, Integer> aplicados, String motivo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                Map<Long, Integer> inverso = new TreeMap<>();
                aplicados.forEach((productoId, cantidad) -> inverso.put(productoId, -cantidad));
                try {
                    inventarioService.moverStock(depositoId, inverso, "Reversión: " + motivo);
                    log.warn("La operación no se completó; se revirtió el stock del depósito {}: {} ({})",
                            depositoId, inverso, motivo);
                } catch (RuntimeException ex) {
                    log.error("No se pudo revertir el stock del depósito {}: {} ({}). Requiere corrección manual.",
                            depositoId, inverso, motivo, ex);
                }
            }
        });
    }

    private void acumular(Map<StockKey, Integer> changes, StockKey key, int quantity) {
        try {
            changes.merge(key, quantity, Math::addExact);
        } catch (ArithmeticException exception) {
            throw new BusinessRuleException(
                    "La cantidad total del producto %d excede el rango permitido.".formatted(key.productoId()));
        }
    }

    private record StockKey(Long depositoId, Long productoId) {
    }
}
