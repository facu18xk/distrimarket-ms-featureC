package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.dto.FacturaVentaDetalleResponseDTO;
import com.distrimarket.commons.entity.FacturaVentaDetalle;
import com.distrimarket.commons.dto.FacturaVentaDetalleRequestDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = EntityReferenceMapper.class)
public interface FacturaVentaDetalleMapper
        extends BaseMapper<FacturaVentaDetalleRequestDTO, FacturaVentaDetalleResponseDTO, FacturaVentaDetalle> {

    @Override
    @Mapping(source = "id", target = "idDetalle")
    @Mapping(source = "producto.id", target = "idProducto")
    @Mapping(source = "producto.nombre", target = "nombreProducto")
    FacturaVentaDetalleResponseDTO toDto(FacturaVentaDetalle entity);

    @Mapping(source = "idProducto", target = "producto", qualifiedByName = "productoReference")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "facturaVenta", ignore = true)
    @Mapping(target = "precioUnitario", ignore = true)
    @Mapping(target = "porcentajeIva", ignore = true)
    FacturaVentaDetalle toEntity(FacturaVentaDetalleRequestDTO request);
}
