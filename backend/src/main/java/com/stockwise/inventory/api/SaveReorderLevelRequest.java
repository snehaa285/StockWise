package com.stockwise.inventory.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SaveReorderLevelRequest(
        @NotNull UUID productId,
        @NotNull UUID locationId,
        @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 3)
                BigDecimal reorderLevel) {
}
