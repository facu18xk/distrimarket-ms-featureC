package com.distrimarket.ms.featurec.exception;

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
