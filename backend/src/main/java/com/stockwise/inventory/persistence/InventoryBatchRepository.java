package com.stockwise.inventory.persistence;

import com.stockwise.inventory.application.ExpiryStock;
import com.stockwise.inventory.domain.InventoryBatch;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, UUID> {
    List<InventoryBatch> findAllByBusiness_IdAndProduct_IdOrderByCreatedAtDesc(
            UUID businessId, UUID productId);

    @Query("select new com.stockwise.inventory.application.ExpiryStock("
            + "batch.id, batch.product.id, batch.product.name, batch.product.sku, "
            + "batch.product.unitOfMeasure, balance.location.id, balance.location.name, "
            + "batch.batchCode, batch.expiryDate, balance.quantity) "
            + "from InventoryStockBalance balance join balance.batch batch "
            + "where balance.business.id = :businessId "
            + "and balance.quantity > 0 "
            + "and batch.expiryDate between :fromDate and :toDate "
            + "and batch.product.archivedAt is null "
            + "and balance.location.archivedAt is null "
            + "order by batch.expiryDate, batch.product.name, balance.location.name")
    List<ExpiryStock> findPositiveStockByExpiryDate(
            @Param("businessId") UUID businessId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("select new com.stockwise.inventory.application.ExpiryStock("
            + "batch.id, batch.product.id, batch.product.name, batch.product.sku, "
            + "batch.product.unitOfMeasure, balance.location.id, balance.location.name, "
            + "batch.batchCode, batch.expiryDate, balance.quantity) "
            + "from InventoryStockBalance balance join balance.batch batch "
            + "where balance.business.id = :businessId "
            + "and balance.quantity > 0 "
            + "and batch.expiryDate < :asOfDate "
            + "and batch.product.archivedAt is null "
            + "and balance.location.archivedAt is null "
            + "order by batch.expiryDate, batch.product.name, balance.location.name")
    List<ExpiryStock> findPositiveStockBeforeDate(
            @Param("businessId") UUID businessId,
            @Param("asOfDate") LocalDate asOfDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from InventoryBatch batch "
            + "where batch.business.id = :businessId and batch.product.id = :productId "
            + "and batch.normalizedBatchCode = :normalizedCode")
    Optional<InventoryBatch> findByProductAndCodeForUpdate(
            @Param("businessId") UUID businessId,
            @Param("productId") UUID productId,
            @Param("normalizedCode") String normalizedCode);
}
