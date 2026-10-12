package com.distrimarket.ms.featurec.client;

import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InventarioErrorDecoderTest {

    private final InventarioErrorDecoder decoder = new InventarioErrorDecoder();

    @Test
    void noEncontradoDeInventarioEsNoEncontradoEnVentas() {
        Exception error = decoder.decode("obtenerProducto", respuesta(404, "{\"message\":\"Recurso no encontrado con ID: 9\"}"));

        assertInstanceOf(ResourceNotFoundException.class, error);
        assertEquals("Recurso no encontrado con ID: 9", error.getMessage());
    }

    @Test
    void stockInsuficienteDeInventarioEsReglaDeNegocioConSuMensaje() {
        Exception error = decoder.decode("aplicarMovimientos",
                respuesta(409, "{\"status\":409,\"message\":\"Stock insuficiente para el producto 'Leche' (ID 1)\"}"));

        assertInstanceOf(BusinessRuleException.class, error);
        assertEquals("Stock insuficiente para el producto 'Leche' (ID 1)", error.getMessage());
    }

    @Test
    void solicitudInvalidaDeInventarioEsSolicitudInvalidaEnVentas() {
        Exception error = decoder.decode("aplicarMovimientos", respuesta(400, "{\"message\":\"Cantidad inválida\"}"));

        assertInstanceOf(IllegalArgumentException.class, error);
        assertEquals("Cantidad inválida", error.getMessage());
    }

    @Test
    void cuerpoIlegibleUsaUnMensajeGenericoSinDetallesTecnicos() {
        Exception error = decoder.decode("obtenerProducto", respuesta(404, "<html>error</html>"));

        assertInstanceOf(ResourceNotFoundException.class, error);
        assertEquals("Inventario rechazó la operación.", error.getMessage());
    }

    @Test
    void falloInternoDeInventarioQuedaComoErrorDeComunicacion() {
        Exception error = decoder.decode("obtenerProducto", respuesta(500, "{\"message\":\"NullPointerException\"}"));

        assertInstanceOf(FeignException.class, error);
    }

    private static Response respuesta(int status, String cuerpo) {
        Request request = Request.create(Request.HttpMethod.GET, "http://distrimarket-inventario/inventario/x",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(status)
                .reason("")
                .request(request)
                .headers(Map.of())
                .body(cuerpo, StandardCharsets.UTF_8)
                .build();
    }
}
