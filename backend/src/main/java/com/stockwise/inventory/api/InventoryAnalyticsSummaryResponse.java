package com.stockwise.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record InventoryAnalyticsSummaryResponse(
        UUID businessId,
        LocalDate asOfDate,
        Map<String, BigDecimal> onHandByUnitOfMeasure,
        long stockedProductCount,
        long activeLocationCount,
        long lowStockLocationProductCount,
        long upcomingExpiryLocationBatchCount,
        long expiredLocationBatchCount) {
}
