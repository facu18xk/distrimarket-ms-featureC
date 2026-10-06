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
    Timbrado toEntity(TimbradoCreateDTO request);
}
