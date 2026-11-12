package com.stockwise.catalog.api;

import com.stockwise.catalog.domain.AttributeType;
import com.stockwise.catalog.domain.CategoryAttributeDefinition;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AttributeDefinitionResponse(
        UUID id,
        UUID categoryId,
        String name,
        AttributeType type,
        boolean required,
        String description,
        AttributeConstraintsResponse constraints,
        boolean active,
        List<AttributeOptionResponse> options) {
    public static AttributeDefinitionResponse from(
            CategoryAttributeDefinition definition,
            AttributeConstraintsResponse constraints,
            List<AttributeOptionResponse> options) {
        return new AttributeDefinitionResponse(
                definition.getId(),
                definition.getCategory().getId(),
                definition.getName(),
                definition.getType(),
                definition.isRequired(),
                definition.getDescription(),
                constraints,
                definition.isActive(),
                options);
    }

    public record AttributeConstraintsResponse(
            Integer minTextLength,
            Integer maxTextLength,
            BigInteger minInteger,
            BigInteger maxInteger,
            BigDecimal minDecimal,
            BigDecimal maxDecimal,
            LocalDate minDate,
            LocalDate maxDate,
            Integer minSelections,
            Integer maxSelections) {
        public static AttributeConstraintsResponse from(
                CategoryAttributeDefinition definition) {
            return new AttributeConstraintsResponse(
                    definition.getMinTextLength(),
                    definition.getMaxTextLength(),
                    definition.getMinInteger(),
                    definition.getMaxInteger(),
                    definition.getMinDecimal(),
                    definition.getMaxDecimal(),
                    definition.getMinDate(),
                    definition.getMaxDate(),
                    definition.getMinSelections(),
                    definition.getMaxSelections());
        }
    }
}
