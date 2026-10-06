package com.distrimarket.ms.featurec.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class PageableSortSupport {
    private static final Logger log = LoggerFactory.getLogger(PageableSortSupport.class);

    private PageableSortSupport() {
    }

    public static Pageable allowSorts(Pageable pageable, String... allowedProperties) {
        if (pageable.isUnpaged()) {
            return pageable;
        }

        Set<String> allowed = Set.copyOf(Arrays.asList(allowedProperties));
        List<Sort.Order> validOrders = pageable.getSort().stream()
                .filter(order -> allowed.contains(order.getProperty()))
                .toList();
        List<String> rejectedProperties = pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .filter(property -> !allowed.contains(property))
                .toList();

        if (!rejectedProperties.isEmpty()) {
            log.warn("Se ignoraron propiedades de orden no admitidas: {}", rejectedProperties);
        }

        Sort sort = validOrders.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "id")
                : Sort.by(validOrders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
