package com.distrimarket.ms.featurec.config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;

public final class SearchQuerySupport {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT));

    private SearchQuerySupport() {
    }

    public static Optional<LocalDate> parseDate(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(query.trim(), formatter));
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        return Optional.empty();
    }

    public static String digitsOnly(String query) {
        return query == null ? "" : query.replaceAll("\\D", "");
    }
}
