package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.commons.entity.FacturaVentaDetalle;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaDetalleResponseDTO;
import com.distrimarket.commons.entity.BaseEntity;
import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Deposito;
import com.distrimarket.commons.entity.Empleado;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.commons.entity.Producto;
import com.distrimarket.commons.entity.StockDeposito;
import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.commons.dto.EstadoFacturaVenta;
import com.distrimarket.ms.featurec.config.PageableSortSupport;
import com.distrimarket.ms.featurec.config.SearchQuerySupport;
import com.distrimarket.commons.dto.FacturaVentaDetalleRequestDTO;
import com.distrimarket.commons.dto.FacturaVentaRequestDTO;
import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.FacturaVentaDetalleMapper;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import com.distrimarket.ms.featurec.repository.DepositoRepository;
import com.distrimarket.ms.featurec.repository.FacturaVentaDetalleRepository;
import com.distrimarket.ms.featurec.repository.FacturaVentaRepository;
import com.distrimarket.ms.featurec.repository.MedioPagoRepository;
import com.distrimarket.ms.featurec.repository.ProductoRepository;
import com.distrimarket.ms.featurec.repository.StockDepositoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class FacturaVentaService
        extends AbstractBaseService<
        FacturaVenta, FacturaVentaRequestDTO, FacturaVentaResponseDTO,
        FacturaVentaRepository, FacturaVentaMapper> {

    private final FacturaVentaRepository repository;
    private final FacturaVentaDetalleRepository detalleRepository;
    private final FacturaVentaDetalleMapper detalleMapper;
    private final MedioPagoRepository medioPagoRepository;
    private final ProductoRepository productoRepository;
    private final DepositoRepository depositoRepository;
    private final StockDepositoRepository stockDepositoRepository;
    private final EntityManager entityManager;

    public FacturaVentaService(
            FacturaVentaRepository repository,
            FacturaVentaDetalleRepository detalleRepository,
            FacturaVentaMapper mapper,
            FacturaVentaDetalleMapper detalleMapper,
            MedioPagoRepository medioPagoRepository,
            ProductoRepository productoRepository,
            DepositoRepository depositoRepository,
            StockDepositoRepository stockDepositoRepository,
            EntityManager entityManager) {
        super(repository, mapper, "Factura de venta");
        this.repository = repository;
        this.detalleRepository = detalleRepository;
        this.detalleMapper = detalleMapper;
        this.medioPagoRepository = medioPagoRepository;
        this.productoRepository = productoRepository;
        this.depositoRepository = depositoRepository;
        this.stockDepositoRepository = stockDepositoRepository;
        this.entityManager = entityManager;
    }

    private void validarEditable(FacturaVenta factura) {
        if ("ANULADA".equalsIgnoreCase(factura.getEstado())) {
            throw new ConflictException("No se puede modificar una factura anulada.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FacturaVenta get(Long id) {
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
        prepararDetalles(entity);
        ajustarStock(stockChanges(entity, 1));
        entity.recalcularTotales();
        log.info("Emitiendo factura de venta {}", entity.getNumeroFactura());
        FacturaVenta guardada = repository.saveAndFlush(entity);
        return repository.findDetailedById(guardada.getId()).orElse(guardada);
    }

    @Transactional
    public FacturaVenta actualizarEstado(Long id, EstadoFacturaVenta estado) {
        FacturaVenta factura = get(id);
        if (EstadoFacturaVenta.EMITIDA.name().equals(factura.getEstado())
                && estado == EstadoFacturaVenta.ANULADA) {
            ajustarStock(stockChanges(factura, -1));
        } else if (EstadoFacturaVenta.ANULADA.name().equals(factura.getEstado())
                && estado == EstadoFacturaVenta.EMITIDA) {
            ajustarStock(stockChanges(factura, 1));
        }
        factura.setEstado(estado.name());
        log.info("Actualizando estado de factura {} a {}", factura.getNumeroFactura(), estado);
        return repository.save(factura);
    }

    @Transactional(readOnly = true)
    public Page<FacturaVentaDetalleResponseDTO> listarDetalles(
            Long facturaId,
            String query,
            Pageable pageable) {
        obtenerFactura(facturaId);
        Specification<FacturaVentaDetalle> specification = (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("facturaVenta").get("id"), facturaId);
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.like(builder.lower(root.get("producto").get("nombre")), pattern));
        }
        Pageable sortedPageable = PageableSortSupport.allowSorts(pageable,
                "id", "fechaCreacion", "fechaModificacion",
                "cantidad", "precioUnitario", "porcentajeIva", "subtotal");
        return detalleRepository.findAll(specification, sortedPageable).map(detalleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<FacturaVenta> search(
            String query,
            Pageable pageable) {
        Specification<FacturaVenta> specification = (root, criteriaQuery, builder) -> builder.conjunction();
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
        return search(specification, sortedPageable);
    }

    @Transactional(readOnly = true)
    public FacturaVentaDetalleResponseDTO obtenerDetalle(Long facturaId, Long detalleId) {
        FacturaVenta factura = obtenerFactura(facturaId);
        return detalleMapper.toDto(buscarDetalle(factura, detalleId));
    }

    private FacturaVentaDetalle buscarDetalle(FacturaVenta factura, Long detalleId) {
        validarId(detalleId);
        FacturaVentaDetalle detalle = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new ResourceNotFoundException("Detalle de factura de venta", detalleId));
        if (!factura.getId().equals(detalle.getFacturaVenta().getId())) {
            throw new ResourceNotFoundException("Detalle de factura de venta", detalleId);
        }
        return detalle;
    }

    @Transactional
    public FacturaVentaDetalleResponseDTO agregarDetalle(Long facturaId, FacturaVentaDetalleRequestDTO request) {
        FacturaVenta factura = obtenerFactura(facturaId);
        validarEditable(factura);
        FacturaVentaDetalle detalle = detalleMapper.toEntity(request);
        prepararDetalle(detalle);
        ajustarStock(Map.of(new StockKey(factura.getDeposito().getId(),
                detalle.getProducto().getId()), detalle.getCantidad()));
        factura.agregarDetalle(detalle);
        factura.recalcularTotales();
        FacturaVentaDetalle guardado = detalleRepository.save(detalle);
        repository.save(factura);
        return detalleMapper.toDto(guardado);
    }

    @Transactional
    public FacturaVentaDetalleResponseDTO actualizarDetalle(
            Long facturaId,
            Long detalleId,
            FacturaVentaDetalleRequestDTO request) {
        FacturaVenta factura = obtenerFactura(facturaId);
        validarEditable(factura);
        FacturaVentaDetalle detalle = buscarDetalle(factura, detalleId);
        FacturaVentaDetalle cambios = detalleMapper.toEntity(request);
        prepararDetalle(cambios);
        Map<StockKey, Integer> stockChanges = new HashMap<>();
        acumular(stockChanges, new StockKey(factura.getDeposito().getId(),
                detalle.getProducto().getId()), -detalle.getCantidad());
        acumular(stockChanges, new StockKey(factura.getDeposito().getId(),
                cambios.getProducto().getId()), cambios.getCantidad());
        ajustarStock(stockChanges);
        detalle.setProducto(cambios.getProducto());
        detalle.setCantidad(cambios.getCantidad());
        detalle.setPrecioUnitario(cambios.getPrecioUnitario());
        detalle.setPorcentajeIva(cambios.getPorcentajeIva());
        detalle.calcularSubtotal();
        FacturaVentaDetalle actualizado = detalleRepository.save(detalle);
        factura.recalcularTotales();
        repository.save(factura);
        return detalleMapper.toDto(actualizado);
    }

    @Override
    protected void copiarCambios(FacturaVenta target, FacturaVenta source) {
        validar(source);
        validarEditable(target);
        resolverReferencias(source);
        prepararDetalles(source);
        Map<StockKey, Integer> stockChanges = stockChanges(source, 1);
        restarStockActual(stockChanges, target);
        ajustarStock(stockChanges);
        target.setDeposito(source.getDeposito());
        target.setMedioPago(source.getMedioPago());
        target.setNumeroFactura(source.getNumeroFactura());
        target.setFechaEmision(source.getFechaEmision());
        target.setCliente(source.getCliente());
        target.setEmpleado(source.getEmpleado());
        target.setTimbrado(source.getTimbrado());
        if (target.getDetalles() == null) {
            target.setDetalles(new ArrayList<>());
        } else {
            target.getDetalles().clear();
        }
        if (source.getDetalles() != null) {
            target.getDetalles().addAll(source.getDetalles());
            for (FacturaVentaDetalle detalle : target.getDetalles()) {
                detalle.setFacturaVenta(target);
            }
        }
        target.recalcularTotales();
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
        if (entity.getFechaEmision() == null) {
            entity.setFechaEmision(LocalDate.now());
        }
        if (entity.getDetalles() == null || entity.getDetalles().isEmpty()) {
            throw new BusinessRuleException("La factura debe contener al menos un detalle.");
        }
        if ("ANULADA".equalsIgnoreCase(entity.getEstado())) {
            throw new IllegalArgumentException("No se puede emitir una factura anulada.");
        }
        if (entity.getEstado() == null) {
            entity.setEstado("EMITIDA");
        }
    }

    private void prepararDetalles(FacturaVenta entity) {
        for (FacturaVentaDetalle detalle : entity.getDetalles()) {
            prepararDetalle(detalle);
            detalle.setFacturaVenta(entity);
        }
    }

    private void prepararDetalle(FacturaVentaDetalle detalle) {
        if (detalle == null || detalle.getProducto() == null || detalle.getProducto().getId() == null) {
            throw new IllegalArgumentException("Cada detalle debe indicar un producto.");
        }
        Long productoId = detalle.getProducto().getId();
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productoId));
        if (!Boolean.TRUE.equals(producto.getEstado())) {
            throw new BusinessRuleException("El producto " + producto.getId() + " está inactivo.");
        }
        detalle.setProducto(producto);
        detalle.setPrecioUnitario(producto.getPrecioVenta());
        detalle.setPorcentajeIva(producto.getPorcentajeIva());
        validarDetalle(detalle);
    }

    private void resolverReferencias(FacturaVenta entity) {
        entity.setCliente(resolver(Cliente.class, entity.getCliente(), "Cliente"));
        entity.setEmpleado(resolver(Empleado.class, entity.getEmpleado(), "Empleado"));
        if (entity.getDeposito() == null || entity.getDeposito().getId() == null) {
            throw new IllegalArgumentException("Depósito es obligatorio.");
        }
        Long depositoId = entity.getDeposito().getId();
        Deposito deposito = depositoRepository.findById(depositoId)
                .orElseThrow(() -> new ResourceNotFoundException("Depósito", depositoId));
        if (!Boolean.TRUE.equals(deposito.getEstado())) {
            throw new BusinessRuleException("El depósito está inactivo.");
        }
        entity.setDeposito(deposito);
        MedioPago medioPago = entity.getMedioPago();
        if (medioPago == null || medioPago.getId() == null) {
            throw new IllegalArgumentException("Medio de pago es obligatorio.");
        }
        entity.setMedioPago(medioPagoRepository.findById(medioPago.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Medio de pago", medioPago.getId())));
        entity.setTimbrado(resolver(Timbrado.class, entity.getTimbrado(), "Timbrado"));
        if (Boolean.FALSE.equals(entity.getCliente().getEstado())) {
            throw new BusinessRuleException("El cliente está inactivo.");
        }
        if (Boolean.FALSE.equals(entity.getMedioPago().getActivo())) {
            throw new BusinessRuleException("El medio de pago está inactivo.");
        }
        if (Boolean.FALSE.equals(entity.getTimbrado().getActivo())) {
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

    private FacturaVenta obtenerFactura(Long facturaId) {
        validarId(facturaId);
        FacturaVenta factura = repository.findById(facturaId)
                .orElseThrow(() -> new ResourceNotFoundException("Factura de venta", facturaId));
        return validarActivo(factura, facturaId);
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

    private void ajustarStock(Map<StockKey, Integer> changes) {
        var orderedChanges = changes.entrySet().stream()
                .filter(entry -> entry.getValue() != 0)
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(StockKey::depositoId)
                        .thenComparing(StockKey::productoId)))
                .toList();

        Map<StockKey, StockDeposito> stockRows = new HashMap<>();
        Map<StockKey, Integer> resultingQuantities = new HashMap<>();
        for (var entry : orderedChanges) {
            StockKey key = entry.getKey();
            StockDeposito stock = stockDepositoRepository
                    .findByDeposito_IdAndProducto_Id(key.depositoId(), key.productoId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "No hay stock registrado para el producto %d en el depósito %d."
                                    .formatted(key.productoId(), key.depositoId())));
            stockRows.put(key, stock);

            int available = stock.getCantidad() == null ? 0 : stock.getCantidad();
            int requestedChange = entry.getValue();
            long resultingQuantity = (long) available - requestedChange;
            if (requestedChange > 0 && available < requestedChange) {
                throw new BusinessRuleException(
                        "Stock insuficiente para el producto %d en el depósito %d: disponible %d, solicitado %d."
                                .formatted(key.productoId(), key.depositoId(), available, requestedChange));
            }
            if (resultingQuantity < 0 || resultingQuantity > Integer.MAX_VALUE) {
                throw new BusinessRuleException(
                        "El ajuste de stock del producto %d en el depósito %d excede el rango permitido."
                                .formatted(key.productoId(), key.depositoId()));
            }
            resultingQuantities.put(key, (int) resultingQuantity);
        }

        for (var entry : orderedChanges) {
            StockDeposito stock = stockRows.get(entry.getKey());
            stock.setCantidad(resultingQuantities.get(entry.getKey()));
            stockDepositoRepository.save(stock);
        }
    }

    private void acumular(Map<StockKey, Integer> changes, StockKey key, int quantity) {
        changes.merge(key, quantity, Integer::sum);
    }

    private record StockKey(Long depositoId, Long productoId) {
    }
}
