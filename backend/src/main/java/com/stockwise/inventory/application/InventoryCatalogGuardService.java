package com.stockwise.inventory.application;

import com.stockwise.catalog.application.InventoryCatalogGuard;
import com.stockwise.inventory.persistence.InventoryMovementRepository;
import com.stockwise.inventory.persistence.InventoryStockBalanceRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class InventoryCatalogGuardService implements InventoryCatalogGuard {
    private final InventoryStockBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryCatalogGuardService(
            InventoryStockBalanceRepository balanceRepository,
            InventoryMovementRepository movementRepository) {
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasInventoryHistory(UUID businessId, UUID productId) {
        return balanceRepository.existsByBusiness_IdAndProduct_Id(businessId, productId)
                || movementRepository.existsByBusiness_IdAndProduct_Id(businessId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPositiveStock(UUID businessId, UUID productId) {
        return balanceRepository.existsByBusiness_IdAndProduct_IdAndQuantityGreaterThan(
                businessId, productId, BigDecimal.ZERO);
    }
}
