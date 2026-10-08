package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.dto.*;
import com.distrimarket.commons.entity.*;
import com.distrimarket.commons.dto.FacturaVentaRequestDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
