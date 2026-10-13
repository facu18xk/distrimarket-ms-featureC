package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.dto.*;
import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Persona;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClienteMapper extends BaseMapper<ClienteRequestDTO, ClienteResponseDTO, Cliente> {

    @Override
    @Mapping(source = "id", target = "idCliente")
    @Mapping(source = "persona", target = "persona")
    ClienteResponseDTO toDto(Cliente entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "activo", ignore = true)
    Persona toPersona(PersonaRequestDTO dto);

    PersonaResponseDTO toPersonaResponse(Persona entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "estado", ignore = true)
    Cliente toEntity(ClienteRequestDTO request);
}
