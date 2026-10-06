package com.distrimarket.ms.featurec.mapper;

import com.distrimarket.commons.entity.BaseEntity;

public interface BaseMapper<REQUEST_DTO, RESPONSE_DTO, E extends BaseEntity> {

    RESPONSE_DTO toDto(E entity);

    E toEntity(REQUEST_DTO request);
}
