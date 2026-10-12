package com.distrimarket.ms.featurec.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchQuerySupportTest {

    @Test
    void parsesSupportedDates() {
        LocalDate expected = LocalDate.of(2026, 10, 6);

        assertEquals(expected, SearchQuerySupport.parseDate("2026-10-06").orElseThrow());
        assertEquals(expected, SearchQuerySupport.parseDate("6/10/2026").orElseThrow());
        assertEquals(expected, SearchQuerySupport.parseDate("6-10-2026").orElseThrow());
        assertTrue(SearchQuerySupport.parseDate("31/02/2026").isEmpty());
        assertTrue(SearchQuerySupport.parseDate("sin fecha").isEmpty());
    }

    @Test
    void extractsInvoiceDigitsWithoutSeparators() {
        assertEquals("0010010001234", SearchQuerySupport.digitsOnly("001-001-0001234"));
        assertEquals("", SearchQuerySupport.digitsOnly("sin números"));
    }
}
