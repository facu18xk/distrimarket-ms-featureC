package com.distrimarket.ms.featurec.security;

import com.distrimarket.commons.dto.ClienteResponseDTO;
import com.distrimarket.commons.dto.MedioPagoDTO;
import com.distrimarket.ms.featurec.client.InventarioClient;
import com.distrimarket.ms.featurec.controller.ClienteController;
import com.distrimarket.ms.featurec.controller.FacturaVentaController;
import com.distrimarket.ms.featurec.controller.MedioPagoController;
import com.distrimarket.ms.featurec.exception.GlobalExceptionHandler;
import com.distrimarket.ms.featurec.mapper.ClienteMapper;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import com.distrimarket.ms.featurec.mapper.MedioPagoMapper;
import com.distrimarket.ms.featurec.service.ClienteService;
import com.distrimarket.ms.featurec.service.FacturaVentaService;
import com.distrimarket.ms.featurec.service.MedioPagoService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadena de seguridad real de ventas (filtro JWT + reglas por URL + @PreAuthorize),
 * con tokens firmados igual que los emite distrimarket-ms-auth.
 */
@WebMvcTest(controllers = {ClienteController.class, MedioPagoController.class, FacturaVentaController.class})
@Import({SecurityConfig.class, JsonSecurityErrorHandler.class, GlobalExceptionHandler.class})
class SecurityRulesTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SecurityProperties properties;

    @MockBean
    private ClienteService clienteService;
    @MockBean
    private ClienteMapper clienteMapper;
    @MockBean
    private MedioPagoService medioPagoService;
    @MockBean
    private MedioPagoMapper medioPagoMapper;
    @MockBean
    private FacturaVentaService facturaVentaService;
    @MockBean
    private FacturaVentaMapper facturaVentaMapper;
    @MockBean
    private InventarioClient inventarioClient;

    private String token(Instant emitido, String... roles) {
        SecretKeySpec key = new SecretKeySpec(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject("prueba")
                .issuedAt(emitido)
                .expiresAt(emitido.plus(Duration.ofMinutes(30)))
                .claim("roles", List.of(roles))
                .build();
        return "Bearer " + new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String token(String... roles) {
        return token(Instant.now(), roles);
    }

    @Test
    void sinTokenDevuelve401EnJson() throws Exception {
        mvc.perform(get("/clientes/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message", containsString("Authorization")));
    }

    @Test
    void tokenVencidoDevuelve401() throws Exception {
        mvc.perform(get("/clientes/1").header("Authorization", token(Instant.now().minus(Duration.ofHours(2)), "ADMIN")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("expiró")));
    }

    @Test
    void unCompradorNoEntraAClientesPeroSiLeeMediosDePago() throws Exception {
        when(medioPagoService.obtener(1L)).thenReturn(new MedioPagoDTO());

        mvc.perform(get("/clientes/1").header("Authorization", token("COMPRADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/medios-pago/1").header("Authorization", token("COMPRADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void unVendedorConsultaClientesPeroNoLosDaDeBaja() throws Exception {
        when(clienteService.obtener(1L)).thenReturn(new ClienteResponseDTO());

        mvc.perform(get("/clientes/1").header("Authorization", token("VENDEDOR")))
                .andExpect(status().isOk());
        // La URL lo permite; lo rechaza @PreAuthorize del método (403 claro, no 500).
        mvc.perform(delete("/clientes/1").header("Authorization", token("VENDEDOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tiene permisos para realizar esta operación."));
        verify(clienteService, never()).eliminar(anyLong());
    }

    @Test
    void unVendedorNoModificaCatalogos() throws Exception {
        mvc.perform(post("/medios-pago").header("Authorization", token("VENDEDOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Cheque\"}"))
                .andExpect(status().isForbidden());
        verify(medioPagoService, never()).crear(any());
    }

    @Test
    void unUsuarioConVariosRolesSumaSusPermisos() throws Exception {
        when(facturaVentaService.search(nullable(String.class), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        String compradorYAdmin = token("COMPRADOR", "ADMIN");
        mvc.perform(get("/facturas-venta").header("Authorization", compradorYAdmin))
                .andExpect(status().isOk());
        mvc.perform(delete("/clientes/1").header("Authorization", compradorYAdmin))
                .andExpect(status().isNoContent());
        verify(clienteService).eliminar(1L);
    }
}
