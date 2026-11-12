package com.stockwise.catalog.application;

import java.util.UUID;

public interface InventoryCatalogGuard {
    boolean hasInventoryHistory(UUID businessId, UUID productId);

    boolean hasPositiveStock(UUID businessId, UUID productId);
}
