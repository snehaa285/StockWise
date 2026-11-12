package com.stockwise.catalog.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record SaveProductRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 100) String sku,
        @NotBlank @Size(max = 40) String unitOfMeasure,
        @Size(max = 1000) String description,
        Boolean batchTrackingOverride,
        Boolean expiryTrackingOverride,
        @NotNull Map<UUID, JsonNode> attributes) {
}
