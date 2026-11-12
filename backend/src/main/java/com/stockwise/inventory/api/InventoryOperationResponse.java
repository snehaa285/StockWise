package com.stockwise.inventory.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InventoryOperationResponse(
        UUID id,
        String operationType,
        String idempotencyKey,
        List<InventoryMovementResponse> movements,
        LocalDateTime createdAt) {
}
