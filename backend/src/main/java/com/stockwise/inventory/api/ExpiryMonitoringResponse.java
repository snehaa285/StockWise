package com.stockwise.inventory.api;

import java.time.LocalDate;
import java.util.List;

public record ExpiryMonitoringResponse(
        LocalDate asOfDate,
        int nearExpiryDays,
        List<ExpiryStockResponse> upcoming,
        List<ExpiryStockResponse> expired) {
}
