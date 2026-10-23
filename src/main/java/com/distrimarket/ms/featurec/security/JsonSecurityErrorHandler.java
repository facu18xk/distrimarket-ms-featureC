package com.distrimarket.ms.featurec.security;

import com.distrimarket.ms.featurec.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 401 y 403 de los filtros de seguridad con el formato de ApiErrorResponse, sin trazas.
 * Los rechazos de @PreAuthorize los responde GlobalExceptionHandler con el mismo formato.
 */
@Slf4j
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        String message = exception instanceof InvalidBearerTokenException
                ? "El token no es válido o ya expiró. Inicie sesión nuevamente."
                : "Se requiere autenticación: envíe el token en el encabezado Authorization (Bearer).";
        log.warn("No autenticado: {} {} -> 401 ({})",
                request.getMethod(), request.getRequestURI(), exception.getMessage());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, message);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.warn("Acceso denegado: {} {} usuario={} autoridades={} -> 403", request.getMethod(),
                request.getRequestURI(), auth == null ? "anónimo" : auth.getName(),
                auth == null ? "[]" : auth.getAuthorities());
        write(request, response, HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este recurso.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build());
    }
}
