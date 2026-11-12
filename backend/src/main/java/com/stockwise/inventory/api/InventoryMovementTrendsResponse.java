package com.stockwise.inventory.api;

import java.time.LocalDate;
import java.util.List;

public record InventoryMovementTrendsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        List<InventoryMovementTrendPoint> points) {
}
