package com.stockwise.inventory.persistence;

import com.stockwise.inventory.application.InventoryStockReportItem;
import com.stockwise.inventory.application.InventoryQuantityByUnit;
import com.stockwise.inventory.domain.InventoryStockBalance;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryStockBalanceRepository
        extends JpaRepository<InventoryStockBalance, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select balance from InventoryStockBalance balance "
            + "where balance.business.id = :businessId "
            + "and balance.product.id = :productId "
            + "and balance.location.id = :locationId "
            + "and ((:batchId is null and balance.batch is null) "
            + "or balance.batch.id = :batchId)")
    Optional<InventoryStockBalance> findBucketForUpdate(
            @Param("businessId") UUID businessId,
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId,
            @Param("batchId") UUID batchId);

    @Query("select balance from InventoryStockBalance balance "
            + "left join balance.batch batch "
            + "where balance.business.id = :businessId "
            + "and (:productId is null or balance.product.id = :productId) "
            + "and (:locationId is null or balance.location.id = :locationId) "
            + "and balance.quantity > 0 "
            + "order by balance.product.name, balance.location.name, batch.batchCode")
    List<InventoryStockBalance> findPositiveBalances(
            @Param("businessId") UUID businessId,
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId);

    @Query(
            value = "select new com.stockwise.inventory.application.InventoryStockReportItem("
                    + "balance.product.id, balance.product.name, balance.product.sku, "
                    + "balance.product.unitOfMeasure, balance.location.id, "
                    + "balance.location.name, batch.id, batch.batchCode, batch.expiryDate, "
                    + "balance.quantity) "
                    + "from InventoryStockBalance balance left join balance.batch batch "
                    + "where balance.business.id = :businessId and balance.quantity > 0 "
                    + "order by balance.product.name, balance.location.name, batch.batchCode",
            countQuery = "select count(balance.id) from InventoryStockBalance balance "
                    + "where balance.business.id = :businessId and balance.quantity > 0")
    Page<InventoryStockReportItem> findStockReport(
            @Param("businessId") UUID businessId, Pageable pageable);

    @Query("select new com.stockwise.inventory.application.InventoryQuantityByUnit("
            + "balance.product.unitOfMeasure, sum(balance.quantity)) "
            + "from InventoryStockBalance balance "
            + "where balance.business.id = :businessId and balance.quantity > 0 "
            + "group by balance.product.unitOfMeasure "
            + "order by balance.product.unitOfMeasure")
    List<InventoryQuantityByUnit> sumPositiveQuantityByUnitForBusiness(
            @Param("businessId") UUID businessId);

    @Query("select count(distinct balance.product.id) from InventoryStockBalance balance "
            + "where balance.business.id = :businessId and balance.quantity > 0")
    long countStockedProductsForBusiness(@Param("businessId") UUID businessId);

    boolean existsByBusiness_IdAndLocation_IdAndQuantityGreaterThan(
            UUID businessId, UUID locationId, BigDecimal quantity);

    boolean existsByBusiness_IdAndProduct_Id(UUID businessId, UUID productId);

    boolean existsByBusiness_IdAndProduct_IdAndQuantityGreaterThan(
            UUID businessId, UUID productId, BigDecimal quantity);
}
