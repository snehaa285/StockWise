package com.stockwise.inventory.persistence;

import com.stockwise.inventory.domain.InventoryOperation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryOperationRepository
        extends JpaRepository<InventoryOperation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from InventoryOperation operation "
            + "where operation.business.id = :businessId "
            + "and operation.idempotencyKey = :key")
    Optional<InventoryOperation> findByBusinessAndKeyForUpdate(
            @Param("businessId") UUID businessId, @Param("key") String idempotencyKey);
}
