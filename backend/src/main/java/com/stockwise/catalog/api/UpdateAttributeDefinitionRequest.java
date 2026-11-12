package com.stockwise.catalog.api;

import com.stockwise.catalog.domain.AttributeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAttributeDefinitionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull AttributeType type,
        boolean required,
        @Size(max = 500) String description,
        @NotNull @Valid AttributeConstraintsRequest constraints) {
}
