package com.stockwise.inventory.persistence;

import com.stockwise.inventory.domain.InventoryMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository
        extends JpaRepository<InventoryMovement, UUID> {
    @Query("select movement from InventoryMovement movement "
            + "where movement.business.id = :businessId "
            + "and (:productId is null or movement.product.id = :productId) "
            + "and (:locationId is null or movement.location.id = :locationId) "
            + "order by movement.createdAt desc, movement.id desc")
    Page<InventoryMovement> findHistory(
            @Param("businessId") UUID businessId,
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId,
            Pageable pageable);

    boolean existsByBusiness_IdAndProduct_Id(UUID businessId, UUID productId);
}
