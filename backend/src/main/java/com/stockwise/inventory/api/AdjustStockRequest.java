package com.stockwise.inventory.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AdjustStockRequest(
        @NotNull UUID productId,
        @NotNull UUID locationId,
        @NotNull AdjustmentDirection direction,
        @NotNull @Positive @Digits(integer = 15, fraction = 3) BigDecimal quantity,
        @Size(max = 120) String batchCode,
        LocalDate expiryDate,
        @NotNull @Size(min = 1, max = 500) String reason) {
}
