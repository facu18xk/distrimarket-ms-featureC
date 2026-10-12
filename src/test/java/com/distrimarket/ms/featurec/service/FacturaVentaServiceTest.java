package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.DepositoResponseDTO;
import com.distrimarket.commons.dto.EmpleadoResponseDTO;
import com.distrimarket.commons.dto.EstadoFacturaVenta;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.commons.dto.ProductoResponseDTO;
import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Deposito;
import com.distrimarket.commons.entity.Empleado;
import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.commons.entity.FacturaVentaDetalle;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.commons.entity.Producto;
import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import com.distrimarket.ms.featurec.repository.FacturaVentaRepository;
import com.distrimarket.ms.featurec.repository.MedioPagoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaVentaServiceTest {

    private static final String NUMERO = "001-001-000099";

    @Mock
    private FacturaVentaRepository facturaRepository;
    @Mock
    private FacturaVentaMapper facturaMapper;
    @Mock
    private MedioPagoRepository medioPagoRepository;
    @Mock
    private InventarioService inventarioService;
    @Mock
    private FacturaVentaResponseAssembler responseAssembler;
    @Mock
    private EntityManager entityManager;

    private FacturaVentaService service;

    @BeforeEach
    void setUp() {
        service = new FacturaVentaService(
                facturaRepository,
                facturaMapper,
                medioPagoRepository,
                inventarioService,
                responseAssembler,
                entityManager);
        // Las referencias JPA a entidades de inventario solo llevan el ID.
        lenient().when(entityManager.getReference(eq(Producto.class), anyLong()))
                .thenAnswer(invocation -> withId(new Producto(), invocation.getArgument(1)));
        lenient().when(entityManager.getReference(eq(Deposito.class), anyLong()))
                .thenAnswer(invocation -> withId(new Deposito(), invocation.getArgument(1)));
        lenient().when(entityManager.getReference(eq(Empleado.class), anyLong()))
                .thenAnswer(invocation -> withId(new Empleado(), invocation.getArgument(1)));
    }

    /** Por defecto inventario sigue teniendo el depósito y los productos de las facturas ya emitidas. */
    @BeforeEach
    void inventoryStillHasWhatWasSold() {
        lenient().when(inventarioService.buscarDeposito(anyLong())).thenReturn(Optional.of(warehouse(true)));
        lenient().when(inventarioService.buscarProducto(anyLong())).thenReturn(Optional.of(new ProductoResponseDTO()));
    }

    @Test
    void cancellingAnInvoiceWithAProductRemovedFromInventoryStillWorksAndReturnsTheRest() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));
        when(facturaRepository.save(invoice)).thenReturn(invoice);
        when(inventarioService.buscarProducto(8L)).thenReturn(Optional.empty());

        FacturaVenta result = service.actualizarEstado(99L, EstadoFacturaVenta.ANULADA);

        assertEquals("ANULADA", result.getEstado());
        verify(inventarioService).moverStock(3L, Map.of(7L, 2), "Anulación de factura " + NUMERO);
    }

    @Test
    void deletingAnInvoiceWhoseWarehouseWasRemovedFromInventoryStillWorksWithoutMovingStock() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));
        when(inventarioService.buscarDeposito(3L)).thenReturn(Optional.empty());

        service.delete(99L);

        assertFalse(invoice.getActivo());
        assertEquals("ANULADA", invoice.getEstado());
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
        verify(facturaRepository).save(invoice);
    }

    @Test
    void reissuingAnInvoiceWithAProductRemovedFromInventoryIsRejected() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        invoice.setEstado("ANULADA");
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));
        doThrow(new BusinessRuleException("Inventario no puede mover el stock"))
                .when(inventarioService).moverStock(3L, Map.of(7L, -2, 8L, -1), "Reemisión de factura " + NUMERO);

        assertThrows(BusinessRuleException.class, () -> service.actualizarEstado(99L, EstadoFacturaVenta.EMITIDA));

        assertEquals("ANULADA", invoice.getEstado());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createUsesCurrentProductPriceAndTaxFromInventory() {
        FacturaVenta invoice = validInvoice();
        inventoryProduct(7L, "1250.00", true);
        savesWithId(invoice, 100L);

        service.create(invoice);

        assertEquals(100L, invoice.getId());
        FacturaVentaDetalle detail = invoice.getDetalles().get(0);
        assertEquals(new BigDecimal("1250.00"), detail.getPrecioUnitario());
        assertEquals(new BigDecimal("10"), detail.getPorcentajeIva());
        assertEquals(7L, detail.getProducto().getId());
        verify(inventarioService).moverStock(3L, Map.of(7L, -2), "Venta factura " + NUMERO);
        verify(facturaRepository).saveAndFlush(invoice);
    }

    @Test
    void createSendsCombinedQuantityAcrossDuplicateProductDetailsAndStopsWhenStockIsInsufficient() {
        FacturaVenta invoice = validInvoice();
        invoice.getDetalles().add(requestedLine(7L, 3));
        inventoryProduct(7L, "1250.00", true);
        doThrow(new BusinessRuleException("Stock insuficiente"))
                .when(inventarioService).moverStock(3L, Map.of(7L, -5), "Venta factura " + NUMERO);

        assertThrows(BusinessRuleException.class, () -> service.create(invoice));

        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRejectsDuplicateProductQuantitiesThatOverflowIntegerRange() {
        FacturaVenta invoice = validInvoice();
        invoice.getDetalles().add(requestedLine(7L, Integer.MAX_VALUE));
        inventoryProduct(7L, "1250.00", true);

        assertThrows(BusinessRuleException.class, () -> service.create(invoice));

        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRejectsInactiveProductWithoutMovingStock() {
        FacturaVenta invoice = validInvoice();
        inventoryProduct(7L, "1250.00", false);

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> service.create(invoice));

        assertEquals("El producto 7 está inactivo.", exception.getMessage());
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRejectsInactiveWarehouse() {
        FacturaVenta invoice = validInvoice();
        when(inventarioService.obtenerDeposito(3L)).thenReturn(warehouse(false));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> service.create(invoice));

        assertEquals("El depósito está inactivo.", exception.getMessage());
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
    }

    @Test
    void createRejectsInactiveEmployee() {
        FacturaVenta invoice = validInvoice();
        when(inventarioService.obtenerEmpleado(2L)).thenReturn(employee(false));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> service.create(invoice));

        assertEquals("El empleado está inactivo.", exception.getMessage());
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void getRejectsNonPositiveIdsBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> service.get(0L));
        assertThrows(IllegalArgumentException.class, () -> service.get(null));

        verify(facturaRepository, never()).findDetailedById(any());
    }

    @Test
    void getHidesLogicallyDeletedInvoices() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        invoice.setActivo(false);
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));

        assertThrows(ResourceNotFoundException.class, () -> service.get(99L));
    }

    @Test
    void updateWithTheSameLinesDoesNotMoveStock() {
        FacturaVenta current = invoiceWithTwoDetails();
        FacturaVenta changes = validInvoice();
        changes.getDetalles().clear();
        changes.getDetalles().add(requestedLine(7L, 2));
        changes.getDetalles().add(requestedLine(8L, 1));
        inventoryProduct(7L, "10.00", true);
        inventoryProduct(8L, "50.00", true);
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(current));
        when(facturaRepository.saveAndFlush(current)).thenReturn(current);

        service.update(99L, changes);

        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
        verify(facturaRepository).saveAndFlush(current);
    }

    @Test
    void updateThatChangesWarehouseReturnsStockToTheOldOneAndTakesItFromTheNewOne() {
        FacturaVenta current = invoiceWithTwoDetails();
        FacturaVenta changes = validInvoice();
        changes.setDeposito(withId(new Deposito(), 4L));
        changes.getDetalles().clear();
        changes.getDetalles().add(requestedLine(7L, 2));
        changes.getDetalles().add(requestedLine(8L, 1));
        inventoryProduct(7L, "10.00", true);
        inventoryProduct(8L, "50.00", true);
        DepositoResponseDTO newWarehouse = warehouse(true);
        newWarehouse.setId(4L);
        when(inventarioService.obtenerDeposito(4L)).thenReturn(newWarehouse);
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(current));
        when(facturaRepository.saveAndFlush(current)).thenReturn(current);

        service.update(99L, changes);

        verify(inventarioService).moverStock(3L, Map.of(7L, 2, 8L, 1), "Modificación de factura " + NUMERO);
        verify(inventarioService).moverStock(4L, Map.of(7L, -2, 8L, -1), "Modificación de factura " + NUMERO);
        assertEquals(4L, current.getDeposito().getId());
    }

    @Test
    void updateOfCancelledInvoiceIsRejectedWithoutContactingInventory() {
        FacturaVenta current = invoiceWithTwoDetails();
        current.setEstado("ANULADA");
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(current));

        assertThrows(ConflictException.class, () -> service.update(99L, validInvoice()));

        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
        verify(facturaRepository, never()).saveAndFlush(current);
    }

    @Test
    void reissueWithoutStockKeepsTheInvoiceCancelled() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        invoice.setEstado("ANULADA");
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));
        doThrow(new BusinessRuleException("Stock insuficiente"))
                .when(inventarioService).moverStock(3L, Map.of(7L, -2, 8L, -1), "Reemisión de factura " + NUMERO);

        assertThrows(BusinessRuleException.class, () -> service.actualizarEstado(99L, EstadoFacturaVenta.EMITIDA));

        assertEquals("ANULADA", invoice.getEstado());
        verify(facturaRepository, never()).save(invoice);
    }

    @Test
    void changingToTheCurrentStateIsAConflictAndDoesNotMoveStock() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));

        assertThrows(ConflictException.class, () -> service.actualizarEstado(99L, EstadoFacturaVenta.EMITIDA));

        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
    }

    @Test
    void createRejectsNumberAlreadyUsedForTheSameTimbradoBeforeMovingStock() {
        FacturaVenta invoice = validInvoice();
        when(facturaRepository.existsByNumeroFacturaAndTimbrado_Id(NUMERO, 5L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(invoice));

        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRejectsEmissionDateOutsideTimbradoValidity() {
        FacturaVenta invoice = validInvoice();
        invoice.setFechaEmision(LocalDate.now().plusDays(10));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> service.create(invoice));

        assertEquals("El timbrado está vencido para la fecha de emisión indicada.", exception.getMessage());
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
    }

    @Test
    void responsesAreBuiltByTheAssemblerSoInventoryDataIsIncluded() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        FacturaVentaResponseDTO response = new FacturaVentaResponseDTO();
        when(responseAssembler.toResponse(invoice)).thenReturn(response);
        when(responseAssembler.toResponses(List.of(invoice))).thenReturn(List.of(response));

        assertSame(response, service.toResponse(invoice));
        assertSame(response, service.toResponses(List.of(invoice)).get(0));
    }

    @Test
    void createReportsProductMissingInInventory() {
        FacturaVenta invoice = validInvoice();
        when(inventarioService.obtenerProducto(7L)).thenThrow(new ResourceNotFoundException("Producto", 7L));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> service.create(invoice));

        assertEquals("Producto con ID 7 no encontrado", exception.getMessage());
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
    }

    @Test
    void createIgnoresAnIdProvidedByTheCallerAndReportsMissingEmployeeBeforePersisting() {
        FacturaVenta invoice = validInvoice();
        invoice.setId(700L);
        when(inventarioService.obtenerEmpleado(2L)).thenThrow(new ResourceNotFoundException("Empleado", 2L));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> service.create(invoice));

        assertEquals("Empleado con ID 2 no encontrado", exception.getMessage());
        assertNull(invoice.getId());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRequiresAtLeastOneDetail() {
        FacturaVenta invoice = headerOnlyInvoice();
        invoice.setDetalles(new ArrayList<>());

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.create(invoice));

        assertEquals("La factura debe contener al menos un detalle.", exception.getMessage());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRejectsUnsupportedInvoiceState() {
        FacturaVenta invoice = headerOnlyInvoice();
        invoice.setDetalles(new ArrayList<>(List.of(requestedLine(7L, 1))));
        invoice.setEstado("DESCONOCIDO");

        assertThrows(IllegalArgumentException.class, () -> service.create(invoice));

        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void updateRequiresAtLeastOneDetail() {
        FacturaVenta currentInvoice = new FacturaVenta();
        currentInvoice.setId(99L);
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(currentInvoice));
        FacturaVenta changes = headerOnlyInvoice();
        changes.setDetalles(new ArrayList<>());

        assertThrows(BusinessRuleException.class, () -> service.update(99L, changes));

        verify(facturaRepository, never()).save(currentInvoice);
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
    }

    @Test
    void actualizarEstadoCanCancelAndReactivateInvoice() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));
        when(facturaRepository.save(invoice)).thenReturn(invoice);

        FacturaVenta result = service.actualizarEstado(99L, EstadoFacturaVenta.ANULADA);

        assertEquals("ANULADA", result.getEstado());
        verify(inventarioService).moverStock(3L, Map.of(7L, 2, 8L, 1), "Anulación de factura " + NUMERO);

        result = service.actualizarEstado(99L, EstadoFacturaVenta.EMITIDA);

        assertEquals("EMITIDA", result.getEstado());
        verify(inventarioService).moverStock(3L, Map.of(7L, -2, 8L, -1), "Reemisión de factura " + NUMERO);
        verify(facturaRepository, times(2)).save(invoice);
    }

    @Test
    void logicalDeleteRestoresStockAndPreservesInvoiceDetails() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));
        when(facturaRepository.save(invoice)).thenReturn(invoice);

        service.delete(99L);

        assertFalse(invoice.getActivo());
        assertEquals("ANULADA", invoice.getEstado());
        assertEquals(2, invoice.getDetalles().size());
        verify(inventarioService).moverStock(3L, Map.of(7L, 2, 8L, 1), "Baja de factura " + NUMERO);
        verify(facturaRepository).save(invoice);
    }

    @Test
    void logicalDeleteOfCancelledInvoiceDoesNotMoveStockAgain() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        invoice.setEstado("ANULADA");
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));

        service.delete(99L);

        assertFalse(invoice.getActivo());
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), anyString());
    }

    @Test
    void updateReconcilesExistingAndNewLinesAndSendsOnlyTheNetStockChange() {
        FacturaVenta current = invoiceWithTwoDetails();
        FacturaVentaDetalle existingProductLine = current.getDetalles().get(0);
        FacturaVenta changes = validInvoice();
        changes.getDetalles().clear();
        changes.getDetalles().add(requestedLine(7L, 3));
        changes.getDetalles().add(requestedLine(9L, 2));
        inventoryProduct(7L, "10.00", true);
        inventoryProduct(9L, "25.00", true);
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(current));
        when(facturaRepository.saveAndFlush(current)).thenReturn(current);

        FacturaVenta updated = service.update(99L, changes);

        assertSame(current, updated);
        assertEquals(2, current.getDetalles().size());
        assertSame(existingProductLine, current.getDetalles().get(0));
        assertEquals(10L, current.getDetalles().get(0).getId());
        assertEquals(3, current.getDetalles().get(0).getCantidad());
        assertEquals(9L, current.getDetalles().get(1).getProducto().getId());
        // Producto 7: de 2 a 3 (descuenta 1). Producto 8: se quita (repone 1). Producto 9: nuevo (descuenta 2).
        verify(inventarioService).moverStock(3L, Map.of(7L, -1, 8L, 1, 9L, -2), "Modificación de factura " + NUMERO);
        verify(facturaRepository).saveAndFlush(current);
    }

    @Test
    void actualizarEstadoRejectsNullStateAsBadRequest() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.actualizarEstado(99L, null));

        assertEquals("El estado de la factura es obligatorio.", exception.getMessage());
        verify(facturaRepository, never()).findDetailedById(99L);
    }

    @Test
    void stockIsReturnedToInventoryWhenTheInvoiceTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        FacturaVenta invoice = validInvoice();
        inventoryProduct(7L, "1250.00", true);
        when(facturaRepository.saveAndFlush(invoice)).thenThrow(new IllegalStateException("fallo al guardar"));

        assertThrows(IllegalStateException.class, () -> service.create(invoice));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(inventarioService).moverStock(3L, Map.of(7L, -2), "Venta factura " + NUMERO);
        verify(inventarioService).moverStock(3L, Map.of(7L, 2), "Reversión: Venta factura " + NUMERO);
    }

    @Test
    void stockIsNotReturnedWhenTheInvoiceTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        FacturaVenta invoice = validInvoice();
        inventoryProduct(7L, "1250.00", true);
        savesWithId(invoice, 100L);

        service.create(invoice);
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(inventarioService, times(1)).moverStock(anyLong(), anyMap(), anyString());
        verify(inventarioService, never()).moverStock(anyLong(), anyMap(), startsWith("Reversión"));
    }

    private static void completeTransaction(int status) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
    }

    private void savesWithId(FacturaVenta invoice, Long id) {
        when(facturaRepository.saveAndFlush(invoice)).thenAnswer(invocation -> {
            assertNull(invoice.getId());
            invoice.setId(id);
            return invoice;
        });
        when(facturaRepository.findDetailedById(id)).thenReturn(Optional.of(invoice));
    }

    private void inventoryProduct(Long id, String price, boolean active) {
        ProductoResponseDTO product = new ProductoResponseDTO();
        product.setId(id);
        product.setNombre("Producto " + id);
        product.setPrecioVenta(new BigDecimal(price));
        product.setPorcentajeIva(new BigDecimal("10"));
        product.setEstado(active);
        when(inventarioService.obtenerProducto(id)).thenReturn(product);
    }

    private static EmpleadoResponseDTO employee(boolean active) {
        EmpleadoResponseDTO employee = new EmpleadoResponseDTO();
        employee.setId(2L);
        employee.setEstado(active);
        return employee;
    }

    private static DepositoResponseDTO warehouse(boolean active) {
        DepositoResponseDTO warehouse = new DepositoResponseDTO();
        warehouse.setId(3L);
        warehouse.setEstado(active);
        return warehouse;
    }

    /** Factura EMITIDA ya guardada: depósito 3, producto 7 x2 y producto 8 x1. */
    private FacturaVenta invoiceWithTwoDetails() {
        FacturaVenta invoice = new FacturaVenta();
        invoice.setId(99L);
        invoice.setNumeroFactura(NUMERO);
        invoice.setEstado("EMITIDA");
        invoice.setDeposito(withId(new Deposito(), 3L));

        invoice.agregarDetalle(detail(10L, 7L, 2, "10.00"));
        invoice.agregarDetalle(detail(11L, 8L, 1, "50.00"));
        return invoice;
    }

    private FacturaVentaDetalle detail(Long detailId, Long productId, int quantity, String price) {
        FacturaVentaDetalle detail = requestedLine(productId, quantity);
        detail.setId(detailId);
        detail.setPrecioUnitario(new BigDecimal(price));
        detail.calcularSubtotal();
        return detail;
    }

    private static FacturaVentaDetalle requestedLine(Long productId, int quantity) {
        FacturaVentaDetalle detail = new FacturaVentaDetalle();
        detail.setProducto(withId(new Producto(), productId));
        detail.setCantidad(quantity);
        return detail;
    }

    private static FacturaVenta headerOnlyInvoice() {
        FacturaVenta invoice = new FacturaVenta();
        invoice.setCliente(new Cliente());
        invoice.setEmpleado(new Empleado());
        invoice.setDeposito(new Deposito());
        invoice.setMedioPago(new MedioPago());
        invoice.setTimbrado(new Timbrado());
        invoice.setNumeroFactura(NUMERO);
        return invoice;
    }

    /**
     * Solicitud válida tal como la entrega el mapper: referencias con ID a cliente 1, empleado 2,
     * depósito 3, medio de pago 4 y timbrado 5, con una línea del producto 7 x2.
     */
    private FacturaVenta validInvoice() {
        Cliente cliente = mock(Cliente.class);
        lenient().when(cliente.getId()).thenReturn(1L);
        lenient().when(cliente.getEstado()).thenReturn(true);
        MedioPago medioPago = mock(MedioPago.class);
        lenient().when(medioPago.getId()).thenReturn(4L);
        lenient().when(medioPago.getActivo()).thenReturn(true);
        Timbrado timbrado = mock(Timbrado.class);
        lenient().when(timbrado.getId()).thenReturn(5L);
        lenient().when(timbrado.getActivo()).thenReturn(true);
        lenient().when(timbrado.getFechaInicio()).thenReturn(LocalDate.now().minusDays(1));
        lenient().when(timbrado.getFechaVencimiento()).thenReturn(LocalDate.now().plusDays(1));

        lenient().when(entityManager.find(Cliente.class, 1L)).thenReturn(cliente);
        lenient().when(entityManager.find(Timbrado.class, 5L)).thenReturn(timbrado);
        lenient().when(medioPagoRepository.findById(4L)).thenReturn(Optional.of(medioPago));
        lenient().when(inventarioService.obtenerEmpleado(2L)).thenReturn(employee(true));
        lenient().when(inventarioService.obtenerDeposito(3L)).thenReturn(warehouse(true));

        FacturaVenta invoice = new FacturaVenta();
        invoice.setId(99L);
        invoice.setNumeroFactura(NUMERO);
        invoice.setFechaEmision(LocalDate.now());
        invoice.setCliente(cliente);
        invoice.setEmpleado(withId(new Empleado(), 2L));
        invoice.setDeposito(withId(new Deposito(), 3L));
        invoice.setMedioPago(medioPago);
        invoice.setTimbrado(timbrado);
        invoice.setDetalles(new ArrayList<>(List.of(requestedLine(7L, 2))));
        return invoice;
    }

    private static <E extends com.distrimarket.commons.entity.BaseEntity> E withId(E entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
