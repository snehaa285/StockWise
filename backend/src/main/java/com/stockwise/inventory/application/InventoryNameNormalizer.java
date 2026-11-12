package com.stockwise.inventory.application;

import java.util.Locale;

final class InventoryNameNormalizer {
    private InventoryNameNormalizer() {
    }

    static String display(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InventoryValidationException(field, "Value cannot be empty.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new InventoryValidationException(field, "Value is too long.");
        }
        return normalized;
    }

    static String normalized(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String normalizedCode(String value) {
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    static String optionalDescription(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
