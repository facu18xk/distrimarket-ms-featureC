package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.dto.FacturaVentaRequestDTO;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaResumenDTO;
import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.commons.entity.Persona;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * La factura se responde plana: datos propios de ventas (cliente, medio de pago, timbrado)
 * con su nombre, y lo que pertenece a inventario (empleado, depósito, producto) solo con su id.
 * Así mostrar una factura no requiere consultar a otro microservicio.
 */
@Mapper(componentModel = "spring", uses = {EntityReferenceMapper.class, FacturaVentaDetalleMapper.class})
public interface FacturaVentaMapper
        extends BaseMapper<FacturaVentaRequestDTO, FacturaVentaResponseDTO, FacturaVenta> {

    /** Factura completa, con sus líneas: GET por id, alta, modificación y cambio de estado. */
    @Override
    @Mapping(source = "id", target = "idFacturaVenta")
    @Mapping(source = "cliente.id", target = "idCliente")
    @Mapping(source = "cliente.persona.nombreCompleto", target = "clienteNombre")
    @Mapping(source = "cliente", target = "clienteDocumento", qualifiedByName = "documento")
    @Mapping(source = "empleado.id", target = "idEmpleado")
    @Mapping(source = "deposito.id", target = "idDeposito")
    @Mapping(source = "medioPago.id", target = "idMedioPago")
    @Mapping(source = "medioPago.nombre", target = "medioPagoNombre")
    @Mapping(source = "timbrado.id", target = "idTimbrado")
    @Mapping(source = "timbrado.numeroTimbrado", target = "numeroTimbrado")
    FacturaVentaResponseDTO toDto(FacturaVenta entity);

    /** Cabecera para los listados: sin líneas ni datos de otros microservicios. */
    @Mapping(source = "id", target = "idFacturaVenta")
    @Mapping(source = "cliente.id", target = "idCliente")
    @Mapping(source = "cliente.persona.nombreCompleto", target = "clienteNombre")
    @Mapping(source = "cliente", target = "clienteDocumento", qualifiedByName = "documento")
    @Mapping(source = "empleado.id", target = "idEmpleado")
    @Mapping(source = "deposito.id", target = "idDeposito")
    @Mapping(source = "medioPago.id", target = "idMedioPago")
    FacturaVentaResumenDTO toResumen(FacturaVenta entity);

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
    @Mapping(target = "activo", ignore = true)
    FacturaVenta toEntity(FacturaVentaRequestDTO request);

    /** CI de una persona física o RUC de una jurídica. */
    @Named("documento")
    default String documento(Cliente cliente) {
        Persona persona = cliente == null ? null : cliente.getPersona();
        if (persona == null) {
            return null;
        }
        return persona.getCi() != null ? persona.getCi() : persona.getRuc();
    }
}
