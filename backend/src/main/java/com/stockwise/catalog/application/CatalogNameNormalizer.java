package com.stockwise.catalog.application;

import java.text.Normalizer;
import java.util.Locale;

public final class CatalogNameNormalizer {
    private CatalogNameNormalizer() {
    }

    public static String displayName(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
    }

    public static String normalized(String value) {
        return displayName(value).toLowerCase(Locale.ROOT);
    }

    public static String normalizedSku(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT);
    }
}
