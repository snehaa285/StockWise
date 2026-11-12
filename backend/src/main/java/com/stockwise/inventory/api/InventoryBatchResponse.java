package com.stockwise.inventory.api;

import com.stockwise.inventory.domain.InventoryBatch;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryBatchResponse(
        UUID id,
        UUID productId,
        String productName,
        String batchCode,
        LocalDate expiryDate,
        LocalDateTime createdAt) {
    public static InventoryBatchResponse from(InventoryBatch batch) {
        return new InventoryBatchResponse(
                batch.getId(),
                batch.getProduct().getId(),
                batch.getProduct().getName(),
                batch.getBatchCode(),
                batch.getExpiryDate(),
                batch.getCreatedAt());
    }
}
