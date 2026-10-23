package com.distrimarket.ms.featurec.exception;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badRequestUsesStandardResponse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/clientes");

        var response = handler.handleBadRequest(new IllegalArgumentException("ID inválido"), request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("/api/v1/clientes", response.getBody().getPath());
    }

    @Test
    void failureTalkingToAnotherMicroserviceIs503WithoutTechnicalDetails() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/ventas/facturas-venta");
        FeignException failure = mock(FeignException.class);
        when(failure.getMessage()).thenReturn("Connection refused: distrimarket-inventario:8081");

        var response = handler.handleRemoteServiceFailure(failure, request);

        assertEquals(503, response.getStatusCode().value());
        assertEquals(503, response.getBody().getStatus());
        assertFalse(response.getBody().getMessage().contains("Connection refused"));
        assertEquals("/ventas/facturas-venta", response.getBody().getPath());
    }

    @Test
    void businessRuleIs422AndConflictIs409WithTheirOwnMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/ventas/facturas-venta");

        var businessRule = handler.handleBusinessRule(new BusinessRuleException("Stock insuficiente"), request);
        var conflict = handler.handleConflict(new ConflictException("Número duplicado"), request);

        assertEquals(422, businessRule.getStatusCode().value());
        assertEquals("Stock insuficiente", businessRule.getBody().getMessage());
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals("Número duplicado", conflict.getBody().getMessage());
    }

    @Test
    void unexpectedFailureDoesNotExposeTechnicalMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/facturas-venta");

        var response = handler.handleGeneral(
                new IllegalStateException("password and database details"), request);

        assertEquals(500, response.getStatusCode().value());
        assertFalse(response.getBody().getMessage().contains("password"));
        assertEquals("/api/v1/facturas-venta", response.getBody().getPath());
    }
}
