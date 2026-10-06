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

    @Mapping(source = "idPersona", target = "id")
    Persona toPersona(PersonaDTO dto);

    Persona toPersona(PersonaCreateDTO dto);

    @Mapping(source = "id", target = "idPersona")
    PersonaDTO toPersonaDto(Persona entity);

    @Override
    Cliente toEntity(ClienteRequestDTO request);
}
