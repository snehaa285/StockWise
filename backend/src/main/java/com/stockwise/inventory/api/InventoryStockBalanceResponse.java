package com.stockwise.inventory.api;

import com.stockwise.inventory.domain.InventoryStockBalance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryStockBalanceResponse(
        UUID productId,
        String productName,
        String sku,
        String unitOfMeasure,
        boolean productActive,
        UUID locationId,
        String locationName,
        UUID batchId,
        String batchCode,
        LocalDate expiryDate,
        BigDecimal quantity,
        LocalDateTime updatedAt) {
    public static InventoryStockBalanceResponse from(InventoryStockBalance balance) {
        return new InventoryStockBalanceResponse(
                balance.getProduct().getId(),
                balance.getProduct().getName(),
                balance.getProduct().getSku(),
                balance.getProduct().getUnitOfMeasure(),
                balance.getProduct().isActive(),
                balance.getLocation().getId(),
                balance.getLocation().getName(),
                balance.getBatch() == null ? null : balance.getBatch().getId(),
                balance.getBatch() == null ? null : balance.getBatch().getBatchCode(),
                balance.getBatch() == null ? null : balance.getBatch().getExpiryDate(),
                balance.getQuantity(),
                balance.getUpdatedAt());
    }
}
