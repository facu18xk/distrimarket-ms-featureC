package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.DepositoResponseDTO;
import com.distrimarket.commons.dto.EmpleadoResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaDetalleResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.commons.dto.PersonaResponseDTO;
import com.distrimarket.commons.dto.ProductoResponseDTO;
import com.distrimarket.commons.entity.BaseEntity;
import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Arma la respuesta de una factura: lo propio de ventas sale de la entidad y lo que pertenece
 * a inventario (nombre del producto, empleado, depósito) se le pide a inventario.
 * Una factura histórica siempre se muestra: si inventario ya no ofrece un dato (producto dado
 * de baja, servicio caído), la respuesta lleva solo su ID.
 */
@Slf4j
@Component
public class FacturaVentaResponseAssembler {

    private final FacturaVentaMapper mapper;
    private final InventarioService inventarioService;

    public FacturaVentaResponseAssembler(FacturaVentaMapper mapper, InventarioService inventarioService) {
        this.mapper = mapper;
        this.inventarioService = inventarioService;
    }

    public FacturaVentaResponseDTO toResponse(FacturaVenta factura) {
        return toResponses(List.of(factura)).get(0);
    }

    /** Cada producto, empleado o depósito distinto se consulta una sola vez para toda la lista. */
    public List<FacturaVentaResponseDTO> toResponses(List<FacturaVenta> facturas) {
        ConsultasInventario inventario = new ConsultasInventario();
        List<FacturaVentaResponseDTO> respuestas = new ArrayList<>(facturas.size());
        for (FacturaVenta factura : facturas) {
            FacturaVentaResponseDTO respuesta = mapper.toDto(factura);
            respuesta.setEmpleado(empleado(idDe(factura.getEmpleado()), inventario));
            respuesta.setDeposito(deposito(idDe(factura.getDeposito()), inventario));
            if (respuesta.getDetalles() != null) {
                for (FacturaVentaDetalleResponseDTO detalle : respuesta.getDetalles()) {
                    inventario.producto(detalle.getIdProducto())
                            .ifPresent(producto -> detalle.setNombreProducto(producto.getNombre()));
                }
            }
            respuestas.add(respuesta);
        }
        return respuestas;
    }

    private Map<String, Object> empleado(Long id, ConsultasInventario inventario) {
        if (id == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        inventario.empleado(id).ifPresent(empleado -> {
            putIfPresent(map, "cargo", empleado.getCargo());
            putIfPresent(map, "fechaIngreso", empleado.getFechaIngreso());
            putIfPresent(map, "estado", empleado.getEstado());
            PersonaResponseDTO persona = empleado.getPersona();
            if (persona != null) {
                Map<String, Object> personaMap = new LinkedHashMap<>();
                putIfPresent(personaMap, "id", persona.getId());
                putIfPresent(personaMap, "tipoPersona", persona.getTipoPersona());
                putIfPresent(personaMap, "nombreCompleto", persona.getNombreCompleto());
                putIfPresent(personaMap, "ci", persona.getCi());
                putIfPresent(personaMap, "ruc", persona.getRuc());
                putIfPresent(personaMap, "telefono", persona.getTelefono());
                putIfPresent(personaMap, "correo", persona.getCorreo());
                putIfPresent(personaMap, "direccion", persona.getDireccion());
                map.put("persona", personaMap);
            }
        });
        return map;
    }

    private Map<String, Object> deposito(Long id, ConsultasInventario inventario) {
        if (id == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        inventario.deposito(id).ifPresent(deposito -> {
            putIfPresent(map, "nombre", deposito.getNombre());
            putIfPresent(map, "ubicacion", deposito.getUbicacion());
            putIfPresent(map, "estado", deposito.getEstado());
        });
        return map;
    }

    // La referencia JPA solo se usa por su ID; nunca se cargan sus datos desde ventas.
    private static Long idDe(BaseEntity referencia) {
        return referencia == null ? null : referencia.getId();
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /** Memoria de las consultas hechas a inventario mientras se arma una respuesta. */
    private final class ConsultasInventario {
        private final Map<Long, Optional<ProductoResponseDTO>> productos = new HashMap<>();
        private final Map<Long, Optional<EmpleadoResponseDTO>> empleados = new HashMap<>();
        private final Map<Long, Optional<DepositoResponseDTO>> depositos = new HashMap<>();
        private boolean disponible = true;

        Optional<ProductoResponseDTO> producto(Long id) {
            return consultar(productos, id, inventarioService::mostrarProducto);
        }

        Optional<EmpleadoResponseDTO> empleado(Long id) {
            return consultar(empleados, id, inventarioService::mostrarEmpleado);
        }

        Optional<DepositoResponseDTO> deposito(Long id) {
            return consultar(depositos, id, inventarioService::mostrarDeposito);
        }

        private <T> Optional<T> consultar(
                Map<Long, Optional<T>> memoria, Long id, Function<Long, Optional<T>> consulta) {
            if (id == null) {
                return Optional.empty();
            }
            Optional<T> conocido = memoria.get(id);
            if (conocido != null) {
                return conocido;
            }
            Optional<T> resultado = Optional.empty();
            if (disponible) {
                try {
                    resultado = consulta.apply(id);
                } catch (FeignException ex) {
                    // Sin inventario no se insiste: el resto de la respuesta sale solo con los ID.
                    disponible = false;
                    log.warn("Inventario no está disponible; las facturas se muestran sin sus datos de inventario: {}",
                            ex.getMessage());
                }
            }
            memoria.put(id, resultado);
            return resultado;
        }
    }
}
