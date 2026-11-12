package com.stockwise.business.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBusinessRequest(@NotBlank @Size(max = 120) String name) {
    public CreateBusinessRequest {
        if (name != null) {
            name = name.trim();
        }
    }
}
