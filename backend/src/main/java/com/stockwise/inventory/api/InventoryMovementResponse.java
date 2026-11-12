package com.stockwise.inventory.api;

import com.stockwise.inventory.domain.InventoryMovement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID id,
        UUID operationId,
        String operationType,
        String movementType,
        UUID productId,
        String productName,
        UUID locationId,
        String locationName,
        UUID batchId,
        String batchCode,
        java.time.LocalDate expiryDate,
        BigDecimal quantityDelta,
        BigDecimal quantityAfter,
        UUID sourceLocationId,
        UUID destinationLocationId,
        String reason,
        UUID actorUserId,
        LocalDateTime createdAt) {
    public static InventoryMovementResponse from(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getOperation().getId(),
                movement.getOperation().getOperationType(),
                movement.getMovementType(),
                movement.getProduct().getId(),
                movement.getProduct().getName(),
                movement.getLocation().getId(),
                movement.getLocation().getName(),
                movement.getBatch() == null ? null : movement.getBatch().getId(),
                movement.getBatch() == null ? null : movement.getBatch().getBatchCode(),
                movement.getBatch() == null ? null : movement.getBatch().getExpiryDate(),
                movement.getQuantityDelta(),
                movement.getQuantityAfter(),
                movement.getSourceLocation() == null
                        ? null : movement.getSourceLocation().getId(),
                movement.getDestinationLocation() == null
                        ? null : movement.getDestinationLocation().getId(),
                movement.getReason(),
                movement.getActor().getId(),
                movement.getCreatedAt());
    }
}
