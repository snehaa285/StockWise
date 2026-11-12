package com.stockwise.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryMovementTrendPoint(
        LocalDate movementDate,
        String movementType,
        String unitOfMeasure,
        long movementCount,
        BigDecimal quantityDelta) {
}
