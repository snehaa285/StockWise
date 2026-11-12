package com.stockwise.inventory.application;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockInventory(
        UUID productId,
        String productName,
        String sku,
        String unitOfMeasure,
        UUID locationId,
        String locationName,
        BigDecimal availableQuantity,
        BigDecimal reorderLevel) {
}
