package com.stockwise.inventory.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateBusinessInventorySettingsRequest(
        @Min(1) @Max(3650) int nearExpiryDays) {
}
