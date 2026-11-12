package com.stockwise.catalog.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

public record AttributeConstraintsRequest(
        Integer minTextLength,
        Integer maxTextLength,
        BigInteger minInteger,
        BigInteger maxInteger,
        BigDecimal minDecimal,
        BigDecimal maxDecimal,
        LocalDate minDate,
        LocalDate maxDate,
        Integer minSelections,
        Integer maxSelections) {
}
