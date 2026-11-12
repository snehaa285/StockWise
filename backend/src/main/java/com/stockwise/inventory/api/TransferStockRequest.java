package com.stockwise.inventory.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferStockRequest(
        @NotNull UUID productId,
        @NotNull UUID sourceLocationId,
        @NotNull UUID destinationLocationId,
        @NotNull @Positive @Digits(integer = 15, fraction = 3) BigDecimal quantity,
        @Size(max = 120) String batchCode,
        @Size(max = 500) String reason) {
}
