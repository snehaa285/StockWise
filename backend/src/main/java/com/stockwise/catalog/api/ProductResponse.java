package com.stockwise.catalog.api;

import com.stockwise.catalog.domain.Product;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String name,
        String sku,
        String unitOfMeasure,
        String description,
        Boolean batchTrackingOverride,
        Boolean expiryTrackingOverride,
        boolean batchTracked,
        boolean expiryTracked,
        boolean active,
        Map<UUID, Object> attributes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static ProductResponse from(
            Product product, Map<UUID, Object> attributes) {
        return new ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getSku(),
                product.getUnitOfMeasure(),
                product.getDescription(),
                product.getBatchTrackingOverride(),
                product.getExpiryTrackingOverride(),
                product.getBatchTrackingOverride() == null
                        ? product.getCategory().isBatchTrackingDefault()
                        : product.getBatchTrackingOverride(),
                product.getExpiryTrackingOverride() == null
                        ? product.getCategory().isExpiryTrackingDefault()
                        : product.getExpiryTrackingOverride(),
                product.isActive(),
                attributes,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
