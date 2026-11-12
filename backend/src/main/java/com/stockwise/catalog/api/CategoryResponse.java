package com.stockwise.catalog.api;

import com.stockwise.catalog.domain.ProductCategory;
import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        boolean batchTrackingDefault,
        boolean expiryTrackingDefault,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isBatchTrackingDefault(),
                category.isExpiryTrackingDefault(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
