package com.distrimarket.ms.featurec.config;

import java.util.Map;

public final class SearchFilterSupport {
    private SearchFilterSupport() {
    }

    public static String query(Map<String, Object> filter, String fallbackQuery) {
        if (filter == null || !filter.containsKey("q")) {
            return fallbackQuery;
        }
        Object query = filter.get("q");
        if (query == null) {
            return null;
        }
        if (query instanceof String value) {
            return value;
        }
        throw new IllegalArgumentException("El campo q debe ser texto.");
    }
}
