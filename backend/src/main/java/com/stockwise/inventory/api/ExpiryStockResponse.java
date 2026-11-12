package com.stockwise.inventory.api;

import com.stockwise.inventory.application.ExpiryStock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpiryStockResponse(
        UUID batchId,
        UUID productId,
        String productName,
        String sku,
        String unitOfMeasure,
        UUID locationId,
        String locationName,
        String batchCode,
        LocalDate expiryDate,
        int daysUntilExpiry,
        BigDecimal quantity) {
    public static ExpiryStockResponse from(ExpiryStock stock, LocalDate asOfDate) {
        return new ExpiryStockResponse(
                stock.batchId(),
                stock.productId(),
                stock.productName(),
                stock.sku(),
                stock.unitOfMeasure(),
                stock.locationId(),
                stock.locationName(),
                stock.batchCode(),
                stock.expiryDate(),
                Math.toIntExact(stock.expiryDate().toEpochDay() - asOfDate.toEpochDay()),
                stock.quantity());
    }
}
