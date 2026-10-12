package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.dto.*;
import com.distrimarket.commons.entity.*;
import com.distrimarket.commons.dto.FacturaVentaRequestDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.LinkedHashMap;
import java.util.Map;

@Mapper(componentModel = "spring",
        uses = {
                EntityReferenceMapper.class,
                ClienteMapper.class,
                TimbradoMapper.class,
                FacturaVentaDetalleMapper.class,
                MedioPagoMapper.class
        })
public interface FacturaVentaMapper
        extends BaseMapper<FacturaVentaRequestDTO, FacturaVentaResponseDTO, FacturaVenta> {

    @Override
    @Mapping(source = "id", target = "idFacturaVenta")
    @Mapping(source = "cliente", target = "cliente")
    @Mapping(source = "empleado", target = "empleado")
    @Mapping(source = "deposito", target = "deposito")
    @Mapping(source = "medioPago", target = "medioPago")
    @Mapping(source = "timbrado", target = "timbrado")
    FacturaVentaResponseDTO toDto(FacturaVenta entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "totalIva", ignore = true)
    @Mapping(target = "totalGeneral", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(source = "idCliente", target = "cliente", qualifiedByName = "clienteReference")
    @Mapping(source = "idEmpleado", target = "empleado", qualifiedByName = "empleadoReference")
    @Mapping(source = "idDeposito", target = "deposito", qualifiedByName = "depositoReference")
    @Mapping(source = "idMedioPago", target = "medioPago", qualifiedByName = "medioPagoReference")
    @Mapping(source = "idTimbrado", target = "timbrado", qualifiedByName = "timbradoReference")
    FacturaVenta toEntity(FacturaVentaRequestDTO request);

    // El contrato expone empleado y depósito como objetos libres, sin DTO propio.
    default Map<String, Object> empleadoToMap(Empleado empleado) {
        if (empleado == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", empleado.getId());
        putIfPresent(map, "cargo", empleado.getCargo());
        putIfPresent(map, "fechaIngreso", empleado.getFechaIngreso());
        putIfPresent(map, "estado", empleado.getEstado());
        Persona persona = empleado.getPersona();
        if (persona != null) {
            Map<String, Object> personaMap = new LinkedHashMap<>();
            personaMap.put("id", persona.getId());
            putIfPresent(personaMap, "tipoPersona", persona.getTipoPersona());
            putIfPresent(personaMap, "nombreCompleto", persona.getNombreCompleto());
            putIfPresent(personaMap, "ci", persona.getCi());
            putIfPresent(personaMap, "ruc", persona.getRuc());
            putIfPresent(personaMap, "telefono", persona.getTelefono());
            putIfPresent(personaMap, "correo", persona.getCorreo());
            putIfPresent(personaMap, "direccion", persona.getDireccion());
            map.put("persona", personaMap);
        }
        return map;
    }

    default Map<String, Object> depositoToMap(Deposito deposito) {
        if (deposito == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", deposito.getId());
        putIfPresent(map, "nombre", deposito.getNombre());
        putIfPresent(map, "ubicacion", deposito.getUbicacion());
        putIfPresent(map, "estado", deposito.getEstado());
        return map;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
