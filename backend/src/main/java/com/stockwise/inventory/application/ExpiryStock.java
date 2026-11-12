package com.stockwise.inventory.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpiryStock(
        UUID batchId,
        UUID productId,
        String productName,
        String sku,
        String unitOfMeasure,
        UUID locationId,
        String locationName,
        String batchCode,
        LocalDate expiryDate,
        BigDecimal quantity) {
}
