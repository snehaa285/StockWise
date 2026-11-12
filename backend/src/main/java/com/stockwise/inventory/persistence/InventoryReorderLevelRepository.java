package com.stockwise.inventory.persistence;

import com.stockwise.inventory.application.LowStockInventory;
import com.stockwise.inventory.domain.InventoryReorderLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReorderLevelRepository
        extends JpaRepository<InventoryReorderLevel, UUID> {
    @EntityGraph(attributePaths = {"product", "location"})
    @Query("select level from InventoryReorderLevel level "
            + "where level.business.id = :businessId "
            + "and level.product.archivedAt is null "
            + "and level.location.archivedAt is null "
            + "order by level.location.name, level.product.name")
    List<InventoryReorderLevel> findAllForBusiness(
            @Param("businessId") UUID businessId);

    Optional<InventoryReorderLevel> findByBusiness_IdAndProduct_IdAndLocation_Id(
            UUID businessId, UUID productId, UUID locationId);

    @Query("select new com.stockwise.inventory.application.LowStockInventory("
            + "level.product.id, level.product.name, level.product.sku, "
            + "level.product.unitOfMeasure, level.location.id, level.location.name, "
            + "coalesce(sum(balance.quantity), 0), level.reorderLevel) "
            + "from InventoryReorderLevel level "
            + "left join InventoryStockBalance balance "
            + "on balance.business.id = level.business.id "
            + "and balance.product.id = level.product.id "
            + "and balance.location.id = level.location.id "
            + "where level.business.id = :businessId "
            + "and level.product.archivedAt is null "
            + "and level.location.archivedAt is null "
            + "group by level.id, level.product.id, level.product.name, level.product.sku, "
            + "level.product.unitOfMeasure, level.location.id, level.location.name, "
            + "level.reorderLevel "
            + "having coalesce(sum(balance.quantity), 0) <= level.reorderLevel "
            + "order by level.location.name, level.product.name")
    List<LowStockInventory> findLowStockForBusiness(
            @Param("businessId") UUID businessId);
}
