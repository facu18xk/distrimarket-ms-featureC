package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.entity.BaseEntity;
import com.distrimarket.ms.featurec.config.PageableSortSupport;
import com.distrimarket.ms.featurec.service.BaseService;
import com.distrimarket.ms.featurec.mapper.BaseMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

public abstract class BaseController<REQUEST_DTO, RESPONSE_DTO, E extends BaseEntity> {

    private final BaseService<E, REQUEST_DTO, RESPONSE_DTO> service;
    private final BaseMapper<REQUEST_DTO, RESPONSE_DTO, E> mapper;

    protected BaseController(BaseService<E, REQUEST_DTO, RESPONSE_DTO> service,
                             BaseMapper<REQUEST_DTO, RESPONSE_DTO, E> mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    protected BaseService<E, REQUEST_DTO, RESPONSE_DTO> service() {
        return service;
    }

    protected BaseMapper<REQUEST_DTO, RESPONSE_DTO, E> mapper() {
        return mapper;
    }

    @GetMapping("/{id}")
    public RESPONSE_DTO get(@PathVariable Long id) {
        return mapper.toDto(service.getForRead(id));
    }

    @PostMapping
    public ResponseEntity<RESPONSE_DTO> create(@RequestBody REQUEST_DTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toDto(service.create(mapper.toEntity(request))));
    }

    @PutMapping("/{id}")
    public RESPONSE_DTO update(@PathVariable Long id, @RequestBody REQUEST_DTO request) {
        return mapper.toDto(service.update(id, mapper.toEntity(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    protected Pageable allowSorts(Pageable pageable, String... properties) {
        return PageableSortSupport.allowSorts(pageable, properties);
    }

}
