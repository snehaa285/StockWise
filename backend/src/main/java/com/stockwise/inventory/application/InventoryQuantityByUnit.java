package com.stockwise.inventory.application;

import java.math.BigDecimal;

public record InventoryQuantityByUnit(String unitOfMeasure, BigDecimal quantity) {
}
