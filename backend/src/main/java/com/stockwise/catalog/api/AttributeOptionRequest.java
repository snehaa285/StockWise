package com.stockwise.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttributeOptionRequest(
        @NotBlank @Size(max = 120) String label,
        boolean active,
        int position) {
}
