package com.stockwise.catalog.api;

import com.stockwise.catalog.domain.CategoryAttributeOption;
import java.util.UUID;

public record AttributeOptionResponse(
        UUID id, String label, boolean active, int position) {
    public static AttributeOptionResponse from(CategoryAttributeOption option) {
        return new AttributeOptionResponse(
                option.getId(), option.getLabel(), option.isActive(), option.getPosition());
    }
}
