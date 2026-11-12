package com.stockwise.business.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBusinessRequest(@NotBlank @Size(max = 120) String name) {
    public UpdateBusinessRequest {
        if (name != null) {
            name = name.trim();
        }
    }
}
