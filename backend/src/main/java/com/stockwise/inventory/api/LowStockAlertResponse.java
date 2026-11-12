package com.stockwise.inventory.api;

import com.stockwise.inventory.application.LowStockInventory;
import java.math.BigDecimal;
import java.util.UUID;

public record LowStockAlertResponse(
        UUID productId,
        String productName,
        String sku,
        String unitOfMeasure,
        UUID locationId,
        String locationName,
        BigDecimal availableQuantity,
        BigDecimal reorderLevel,
        BigDecimal shortfallToReorderLevel) {
    public static LowStockAlertResponse from(LowStockInventory inventory) {
        return new LowStockAlertResponse(
                inventory.productId(),
                inventory.productName(),
                inventory.sku(),
                inventory.unitOfMeasure(),
                inventory.locationId(),
                inventory.locationName(),
                inventory.availableQuantity(),
                inventory.reorderLevel(),
                inventory.reorderLevel().subtract(inventory.availableQuantity()));
    }
}
