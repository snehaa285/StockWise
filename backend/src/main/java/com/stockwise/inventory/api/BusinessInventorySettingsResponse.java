package com.stockwise.inventory.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record BusinessInventorySettingsResponse(
        UUID businessId,
        int nearExpiryDays,
        LocalDateTime updatedAt) {
}
