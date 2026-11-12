package com.stockwise.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveLocationRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description) {
}
