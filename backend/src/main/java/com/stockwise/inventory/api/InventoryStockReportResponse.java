package com.stockwise.inventory.api;

import com.stockwise.inventory.application.InventoryStockReportItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InventoryStockReportResponse(
        UUID businessId,
        LocalDateTime generatedAt,
        Map<String, BigDecimal> onHandByUnitOfMeasure,
        List<InventoryStockReportItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
