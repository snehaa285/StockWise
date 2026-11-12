package com.stockwise.inventory.api;

import com.stockwise.inventory.domain.InventoryLocation;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryLocationResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static InventoryLocationResponse from(InventoryLocation location) {
        return new InventoryLocationResponse(
                location.getId(),
                location.getName(),
                location.getDescription(),
                location.isActive(),
                location.getCreatedAt(),
                location.getUpdatedAt());
    }
}
