package com.distrimarket.ms.featurec.client;

import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.InventarioAccesoDenegadoException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;

/**
 * Traduce las respuestas de error de inventario a las excepciones de ventas,
 * para que el manejador global responda con el mismo formato de siempre.
 */
@Slf4j
public class InventarioErrorDecoder implements ErrorDecoder {

    private static final String MENSAJE_GENERICO = "Inventario rechazó la operación.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorDecoder porDefecto = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == 401 || status == 403) {
            // Ventas reenvía el token del usuario: inventario decide según su rol.
            log.warn("Inventario rechazó por permisos ({}) la llamada {}", status, methodKey);
            return new InventarioAccesoDenegadoException(
                    "Inventario no permite esta operación con su usuario (" + status + "). "
                            + "Revise los roles habilitados en inventario.");
        }
        if (status != 400 && status != 404 && status != 409) {
            // Fallos del propio inventario (5xx): los atiende el manejador global como servicio no disponible.
            return porDefecto.decode(methodKey, response);
        }
        String mensaje = leerMensaje(response);
        if (status == 404) {
            // Quien pidió el dato decide si es un problema (validar) o algo esperable (mostrar un histórico).
            log.debug("Inventario respondió 404 en {}: {}", methodKey, mensaje);
        } else {
            log.warn("Inventario respondió {} en {}: {}", status, methodKey, mensaje);
        }
        return switch (status) {
            case 404 -> new ResourceNotFoundException(mensaje);
            // En los endpoints que usa ventas, el 409 de inventario es stock insuficiente: una regla de negocio.
            case 409 -> new BusinessRuleException(mensaje);
            default -> new IllegalArgumentException(mensaje);
        };
    }

    private String leerMensaje(Response response) {
        if (response.body() == null) {
            return MENSAJE_GENERICO;
        }
        try (InputStream body = response.body().asInputStream()) {
            JsonNode mensaje = objectMapper.readTree(body).path("message");
            return mensaje.isTextual() && !mensaje.asText().isBlank() ? mensaje.asText() : MENSAJE_GENERICO;
        } catch (IOException ex) {
            return MENSAJE_GENERICO;
        }
    }
}
