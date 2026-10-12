package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Deposito;
import com.distrimarket.commons.entity.Empleado;
import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.commons.entity.FacturaVentaDetalle;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.commons.entity.Producto;
import com.distrimarket.commons.entity.StockDeposito;
import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.commons.dto.EstadoFacturaVenta;
import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import com.distrimarket.ms.featurec.repository.DepositoRepository;
import com.distrimarket.ms.featurec.repository.FacturaVentaRepository;
import com.distrimarket.ms.featurec.repository.MedioPagoRepository;
import com.distrimarket.ms.featurec.repository.ProductoRepository;
import com.distrimarket.ms.featurec.repository.StockDepositoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaVentaServiceTest {

    @Mock
    private FacturaVentaRepository facturaRepository;
    @Mock
    private FacturaVentaMapper facturaMapper;
    @Mock
    private MedioPagoRepository medioPagoRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private DepositoRepository depositoRepository;
    @Mock
    private StockDepositoRepository stockDepositoRepository;
    @Mock
    private EntityManager entityManager;

    private FacturaVentaService service;

    @BeforeEach
    void setUp() {
        service = new FacturaVentaService(
                facturaRepository,
                facturaMapper,
                medioPagoRepository,
                productoRepository,
                depositoRepository,
                stockDepositoRepository,
                entityManager);
    }

    @Test
    void createUsesCurrentProductPriceAndTaxInsteadOfRequestValues() {
        FacturaVenta invoice = validInvoice(true);
        Producto product = product();
        when(productoRepository.findById(7L)).thenReturn(Optional.of(product));
        StockDeposito stock = stock(10);
        when(stockDepositoRepository.findByDeposito_IdAndProducto_Id(3L, 7L)).thenReturn(Optional.of(stock));
        when(facturaRepository.saveAndFlush(invoice)).thenAnswer(invocation -> {
            assertNull(invoice.getId());
            invoice.setId(100L);
            return invoice;
        });
        when(facturaRepository.findDetailedById(100L)).thenReturn(Optional.of(invoice));

        service.create(invoice);

        assertEquals(100L, invoice.getId());
        FacturaVentaDetalle detail = invoice.getDetalles().get(0);
        assertEquals(new BigDecimal("1250.00"), detail.getPrecioUnitario());
        assertEquals(new BigDecimal("10"), detail.getPorcentajeIva());
        assertEquals(8, stock.getCantidad());
        verify(facturaRepository).saveAndFlush(invoice);
    }

    @Test
    void createRejectsCombinedQuantityAcrossDuplicateProductDetails() {
        FacturaVenta invoice = validInvoice(true);
        FacturaVentaDetalle secondDetail = new FacturaVentaDetalle();
        secondDetail.setProducto(invoice.getDetalles().get(0).getProducto());
        secondDetail.setCantidad(3);
        invoice.getDetalles().add(secondDetail);
        Producto product = product();
        when(productoRepository.findById(7L)).thenReturn(Optional.of(product));
        when(stockDepositoRepository.findByDeposito_IdAndProducto_Id(3L, 7L)).thenReturn(Optional.of(stock(4)));

        assertThrows(BusinessRuleException.class, () -> service.create(invoice));

        verify(stockDepositoRepository, never()).save(any(StockDeposito.class));
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRejectsDuplicateProductQuantitiesThatOverflowIntegerRange() {
        FacturaVenta invoice = validInvoice(true);
        FacturaVentaDetalle secondDetail = new FacturaVentaDetalle();
        secondDetail.setProducto(invoice.getDetalles().get(0).getProducto());
        secondDetail.setCantidad(Integer.MAX_VALUE);
        invoice.getDetalles().add(secondDetail);
        Producto product = product();
        when(productoRepository.findById(7L)).thenReturn(Optional.of(product));

        assertThrows(BusinessRuleException.class, () -> service.create(invoice));

        verify(stockDepositoRepository, never()).findByDeposito_IdAndProducto_Id(3L, 7L);
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createIgnoresAnIdProvidedByTheCaller() {
        FacturaVenta invoice = validInvoice(false);
        invoice.setId(700L);
        Cliente cliente = (Cliente) invoice.getCliente();
        when(entityManager.find(Cliente.class, 1L)).thenReturn(cliente);
        when(entityManager.find(Empleado.class, 2L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.create(invoice));

        assertNull(invoice.getId());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRequiresAtLeastOneDetail() {
        FacturaVenta invoice = new FacturaVenta();
        invoice.setCliente(org.mockito.Mockito.mock(Cliente.class));
        invoice.setEmpleado(org.mockito.Mockito.mock(Empleado.class));
        invoice.setDeposito(org.mockito.Mockito.mock(Deposito.class));
        invoice.setMedioPago(org.mockito.Mockito.mock(MedioPago.class));
        invoice.setTimbrado(org.mockito.Mockito.mock(Timbrado.class));
        invoice.setNumeroFactura("001-001-000099");
        invoice.setDetalles(new ArrayList<>());

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.create(invoice));

        assertEquals("La factura debe contener al menos un detalle.", exception.getMessage());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void createRejectsUnsupportedInvoiceState() {
        FacturaVenta invoice = new FacturaVenta();
        invoice.setCliente(new Cliente());
        invoice.setEmpleado(new Empleado());
        invoice.setDeposito(new Deposito());
        invoice.setMedioPago(new MedioPago());
        invoice.setTimbrado(new Timbrado());
        invoice.setNumeroFactura("001-001-000099");
        FacturaVentaDetalle detail = new FacturaVentaDetalle();
        detail.setProducto(new Producto());
        detail.setCantidad(1);
        invoice.setDetalles(new ArrayList<>(java.util.List.of(detail)));
        invoice.setEstado("DESCONOCIDO");

        assertThrows(IllegalArgumentException.class, () -> service.create(invoice));

        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void updateRequiresAtLeastOneDetail() {
        FacturaVenta currentInvoice = new FacturaVenta();
        currentInvoice.setId(99L);
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(currentInvoice));
        FacturaVenta changes = new FacturaVenta();
        changes.setCliente(org.mockito.Mockito.mock(Cliente.class));
        changes.setEmpleado(org.mockito.Mockito.mock(Empleado.class));
        changes.setDeposito(org.mockito.Mockito.mock(Deposito.class));
        changes.setMedioPago(org.mockito.Mockito.mock(MedioPago.class));
        changes.setTimbrado(org.mockito.Mockito.mock(Timbrado.class));
        changes.setNumeroFactura("001-001-000099");
        changes.setDetalles(new ArrayList<>());

        assertThrows(BusinessRuleException.class, () -> service.update(99L, changes));

        verify(facturaRepository, never()).save(currentInvoice);
    }

    @Test
    void createReportsMissingEmployeeBeforePersistingInvoice() {
        FacturaVenta invoice = validInvoice(false);
        Cliente cliente = (Cliente) invoice.getCliente();
        when(entityManager.find(Cliente.class, 1L)).thenReturn(cliente);
        when(entityManager.find(Empleado.class, 2L)).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.create(invoice));

        assertEquals("Empleado con ID 2 no encontrado", exception.getMessage());
        verify(facturaRepository, never()).saveAndFlush(invoice);
    }

    @Test
    void actualizarEstadoCanCancelAndReactivateInvoice() {
        FacturaVenta invoice = new FacturaVenta();
        invoice.setId(99L);
        invoice.setEstado("EMITIDA");
        Deposito deposito = org.mockito.Mockito.mock(Deposito.class);
        when(deposito.getId()).thenReturn(3L);
        invoice.setDeposito(deposito);
        FacturaVentaDetalle detail = new FacturaVentaDetalle();
        Producto product = org.mockito.Mockito.mock(Producto.class);
        when(product.getId()).thenReturn(7L);
        detail.setProducto(product);
        detail.setCantidad(2);
        invoice.setDetalles(new ArrayList<>(java.util.List.of(detail)));
        StockDeposito stock = stock(5);
        when(stockDepositoRepository.findByDeposito_IdAndProducto_Id(3L, 7L)).thenReturn(Optional.of(stock));
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));
        when(facturaRepository.save(invoice)).thenReturn(invoice);

        FacturaVenta result = service.actualizarEstado(99L, EstadoFacturaVenta.ANULADA);

        assertEquals("ANULADA", result.getEstado());
        assertEquals(7, stock.getCantidad());
        result = service.actualizarEstado(99L, EstadoFacturaVenta.EMITIDA);
        assertEquals("EMITIDA", result.getEstado());
        assertEquals(5, stock.getCantidad());
        verify(facturaRepository, org.mockito.Mockito.times(2)).save(invoice);
    }

    @Test
    void logicalDeleteRestoresStockAndPreservesInvoiceDetails() {
        FacturaVenta invoice = invoiceWithTwoDetails();
        StockDeposito firstProductStock = stock(5);
        StockDeposito secondProductStock = stock(5);
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(invoice));
        when(stockDepositoRepository.findByDeposito_IdAndProducto_Id(3L, 7L))
                .thenReturn(Optional.of(firstProductStock));
        when(stockDepositoRepository.findByDeposito_IdAndProducto_Id(3L, 8L))
                .thenReturn(Optional.of(secondProductStock));
        when(facturaRepository.save(invoice)).thenReturn(invoice);

        service.delete(99L);

        assertFalse(invoice.getActivo());
        assertEquals("ANULADA", invoice.getEstado());
        assertEquals(2, invoice.getDetalles().size());
        assertEquals(7, firstProductStock.getCantidad());
        assertEquals(6, secondProductStock.getCantidad());
        verify(facturaRepository).save(invoice);
    }

    @Test
    void updateReconcilesExistingAndNewLinesAndAdjustsStockNet() {
        FacturaVenta current = invoiceWithTwoDetails();
        FacturaVentaDetalle existingProductLine = current.getDetalles().get(0);
        Producto product7 = productWithId(7L, "10.00");
        Producto product9 = productWithId(9L, "25.00");
        FacturaVenta changes = validInvoice(false);
        changes.getDetalles().clear();
        changes.getDetalles().add(requestedLine(7L, 3));
        changes.getDetalles().add(requestedLine(9L, 2));
        changes.setId(null);

        Cliente client = (Cliente) changes.getCliente();
        Empleado employee = (Empleado) changes.getEmpleado();
        Deposito warehouse = (Deposito) changes.getDeposito();
        MedioPago paymentMethod = (MedioPago) changes.getMedioPago();
        Timbrado timbrado = (Timbrado) changes.getTimbrado();
        when(warehouse.getId()).thenReturn(3L);
        when(paymentMethod.getId()).thenReturn(4L);
        when(timbrado.getId()).thenReturn(5L);
        when(entityManager.find(Cliente.class, 1L)).thenReturn(client);
        when(entityManager.find(Empleado.class, 2L)).thenReturn(employee);
        when(entityManager.find(Timbrado.class, 5L)).thenReturn(timbrado);
        when(changes.getCliente().getEstado()).thenReturn(true);
        when(changes.getTimbrado().getActivo()).thenReturn(true);
        when(changes.getTimbrado().getFechaInicio()).thenReturn(LocalDate.now().minusDays(1));
        when(changes.getTimbrado().getFechaVencimiento()).thenReturn(LocalDate.now().plusDays(1));
        when(depositoRepository.findById(3L)).thenReturn(Optional.of(warehouse));
        when(warehouse.getEstado()).thenReturn(true);
        when(medioPagoRepository.findById(4L)).thenReturn(Optional.of(paymentMethod));
        when(paymentMethod.getActivo()).thenReturn(true);
        when(productoRepository.findById(7L)).thenReturn(Optional.of(product7));
        when(productoRepository.findById(9L)).thenReturn(Optional.of(product9));
        StockDeposito stock7 = stock(5);
        StockDeposito stock8 = stock(5);
        StockDeposito stock9 = stock(5);
        when(stockDepositoRepository.findByDeposito_IdAndProducto_Id(3L, 7L)).thenReturn(Optional.of(stock7));
        when(stockDepositoRepository.findByDeposito_IdAndProducto_Id(3L, 8L)).thenReturn(Optional.of(stock8));
        when(stockDepositoRepository.findByDeposito_IdAndProducto_Id(3L, 9L)).thenReturn(Optional.of(stock9));
        when(facturaRepository.findDetailedById(99L)).thenReturn(Optional.of(current));
        when(facturaRepository.saveAndFlush(current)).thenReturn(current);

        FacturaVenta updated = service.update(99L, changes);

        assertSame(current, updated);
        assertEquals(2, current.getDetalles().size());
        assertSame(existingProductLine, current.getDetalles().get(0));
        assertEquals(10L, current.getDetalles().get(0).getId());
        assertEquals(3, current.getDetalles().get(0).getCantidad());
        assertEquals(9L, current.getDetalles().get(1).getProducto().getId());
        assertEquals(4, stock7.getCantidad());
        assertEquals(6, stock8.getCantidad());
        assertEquals(3, stock9.getCantidad());
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

    private FacturaVenta invoiceWithTwoDetails() {
        FacturaVenta invoice = new FacturaVenta();
        invoice.setId(99L);
        invoice.setEstado("EMITIDA");
        Deposito deposito = new Deposito();
        deposito.setId(3L);
        invoice.setDeposito(deposito);

        invoice.agregarDetalle(detail(10L, 7L, 2, "10.00"));
        invoice.agregarDetalle(detail(11L, 8L, 1, "50.00"));
        return invoice;
    }

    private FacturaVentaDetalle detail(Long detailId, Long productId, int quantity, String price) {
        FacturaVentaDetalle detail = new FacturaVentaDetalle();
        detail.setId(detailId);
        Producto product = new Producto();
        product.setId(productId);
        detail.setProducto(product);
        detail.setCantidad(quantity);
        detail.setPrecioUnitario(new BigDecimal(price));
        detail.calcularSubtotal();
        return detail;
    }

    private FacturaVentaDetalle requestedLine(Long productId, int quantity) {
        FacturaVentaDetalle detail = new FacturaVentaDetalle();
        Producto product = new Producto();
        product.setId(productId);
        detail.setProducto(product);
        detail.setCantidad(quantity);
        return detail;
    }

    private Producto productWithId(Long id, String price) {
        Producto product = org.mockito.Mockito.mock(Producto.class);
        when(product.getId()).thenReturn(id);
        when(product.getEstado()).thenReturn(true);
        when(product.getPrecioVenta()).thenReturn(new BigDecimal(price));
        when(product.getPorcentajeIva()).thenReturn(new BigDecimal("10"));
        return product;
    }

    private FacturaVenta validInvoice(boolean allReferencesExist) {
        Cliente cliente = org.mockito.Mockito.mock(Cliente.class);
        when(cliente.getId()).thenReturn(1L);
        if (allReferencesExist) {
            when(cliente.getEstado()).thenReturn(true);
        }

        Empleado empleado = org.mockito.Mockito.mock(Empleado.class);
        when(empleado.getId()).thenReturn(2L);

        Deposito deposito = org.mockito.Mockito.mock(Deposito.class);
        MedioPago medioPago = org.mockito.Mockito.mock(MedioPago.class);
        Timbrado timbrado = org.mockito.Mockito.mock(Timbrado.class);
        when(entityManager.find(Cliente.class, 1L)).thenReturn(cliente);
        when(entityManager.find(Empleado.class, 2L)).thenReturn(empleado);
        if (allReferencesExist) {
            when(deposito.getId()).thenReturn(3L);
            when(deposito.getEstado()).thenReturn(true);
            when(depositoRepository.findById(3L)).thenReturn(Optional.of(deposito));
            when(medioPago.getId()).thenReturn(4L);
            when(medioPago.getActivo()).thenReturn(true);
            when(medioPagoRepository.findById(4L)).thenReturn(Optional.of(medioPago));
            when(timbrado.getId()).thenReturn(5L);
            when(timbrado.getActivo()).thenReturn(true);
            when(timbrado.getFechaInicio()).thenReturn(LocalDate.now().minusDays(1));
            when(timbrado.getFechaVencimiento()).thenReturn(LocalDate.now().plusDays(1));
            when(entityManager.find(Timbrado.class, 5L)).thenReturn(timbrado);
        }

        FacturaVenta invoice = new FacturaVenta();
        invoice.setId(99L);
        invoice.setNumeroFactura("001-001-000099");
        invoice.setFechaEmision(LocalDate.now());
        invoice.setCliente(cliente);
        invoice.setEmpleado(empleado);
        invoice.setDeposito(deposito);
        invoice.setMedioPago(medioPago);
        invoice.setTimbrado(timbrado);

        FacturaVentaDetalle detail = new FacturaVentaDetalle();
        Producto productReference = org.mockito.Mockito.mock(Producto.class);
        if (allReferencesExist) {
            when(productReference.getId()).thenReturn(7L);
        }
        detail.setProducto(productReference);
        detail.setCantidad(2);
        invoice.setDetalles(new ArrayList<>(java.util.List.of(detail)));
        return invoice;
    }

    private Producto product() {
        Producto product = org.mockito.Mockito.mock(Producto.class);
        when(product.getId()).thenReturn(7L);
        when(product.getEstado()).thenReturn(true);
        when(product.getPrecioVenta()).thenReturn(new BigDecimal("1250.00"));
        when(product.getPorcentajeIva()).thenReturn(new BigDecimal("10"));
        return product;
    }

    private StockDeposito stock(int quantity) {
        StockDeposito stock = new StockDeposito();
        stock.setCantidad(quantity);
        return stock;
    }
}
