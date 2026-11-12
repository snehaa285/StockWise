package com.stockwise.inventory.api;

import com.stockwise.inventory.domain.InventoryReorderLevel;
import java.math.BigDecimal;
import java.util.UUID;

public record InventoryReorderLevelResponse(
        UUID id,
        UUID productId,
        String productName,
        String sku,
        String unitOfMeasure,
        UUID locationId,
        String locationName,
        BigDecimal reorderLevel) {
    public static InventoryReorderLevelResponse from(InventoryReorderLevel level) {
        return new InventoryReorderLevelResponse(
                level.getId(),
                level.getProduct().getId(),
                level.getProduct().getName(),
                level.getProduct().getSku(),
                level.getProduct().getUnitOfMeasure(),
                level.getLocation().getId(),
                level.getLocation().getName(),
                level.getReorderLevel());
    }
}
