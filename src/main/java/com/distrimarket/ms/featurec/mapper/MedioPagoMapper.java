package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.dto.MedioPagoDTO;
import com.distrimarket.commons.entity.MedioPago;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedioPagoMapper extends BaseMapper<MedioPagoDTO, MedioPagoDTO, MedioPago> {

    @Override
    @Mapping(source = "id", target = "idMedioPago")
    MedioPagoDTO toDto(MedioPago entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "activo", ignore = true)
    MedioPago toEntity(MedioPagoDTO request);
}
