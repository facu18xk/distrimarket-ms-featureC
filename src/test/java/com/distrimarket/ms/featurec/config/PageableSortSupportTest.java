package com.distrimarket.ms.featurec.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageableSortSupportTest {

    @Test
    void unsupportedSortFallsBackToIdDescending() {
        Pageable pageable = PageRequest.of(2, 15, Sort.by("string"));

        Pageable result = PageableSortSupport.allowSorts(pageable, "id", "estado");

        assertEquals(2, result.getPageNumber());
        assertEquals(15, result.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), result.getSort());
    }

    @Test
    void allowedSortIsPreserved() {
        Sort requestedSort = Sort.by(Sort.Direction.ASC, "estado");
        Pageable pageable = PageRequest.of(0, 20, requestedSort);

        Pageable result = PageableSortSupport.allowSorts(pageable, "id", "estado");

        assertEquals(requestedSort, result.getSort());
    }
}
