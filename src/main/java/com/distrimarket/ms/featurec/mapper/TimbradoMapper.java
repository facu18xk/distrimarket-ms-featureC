package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.dto.TimbradoCreateDTO;
import com.distrimarket.commons.dto.TimbradoDTO;
import com.distrimarket.commons.entity.Timbrado;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TimbradoMapper extends BaseMapper<TimbradoCreateDTO, TimbradoDTO, Timbrado> {

    @Override
    @Mapping(source = "id", target = "idTimbrado")
    TimbradoDTO toDto(Timbrado entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "activo", ignore = true)
    Timbrado toEntity(TimbradoCreateDTO request);
}
