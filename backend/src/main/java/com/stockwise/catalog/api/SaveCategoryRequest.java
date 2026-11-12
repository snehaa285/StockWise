package com.stockwise.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        boolean batchTrackingDefault,
        boolean expiryTrackingDefault) {
}
