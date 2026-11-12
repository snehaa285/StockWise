package com.stockwise.inventory.persistence;

import com.stockwise.inventory.domain.InventoryLocation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryLocationRepository
        extends JpaRepository<InventoryLocation, UUID> {
    long countByBusiness_IdAndArchivedAtIsNull(UUID businessId);

    List<InventoryLocation> findAllByBusiness_IdAndArchivedAtIsNullOrderByNameAsc(
            UUID businessId);

    Optional<InventoryLocation> findByIdAndBusiness_IdAndArchivedAtIsNull(
            UUID id, UUID businessId);

    boolean existsByBusiness_IdAndNormalizedNameAndArchivedAtIsNull(
            UUID businessId, String normalizedName);

    boolean existsByBusiness_IdAndNormalizedNameAndArchivedAtIsNullAndIdNot(
            UUID businessId, String normalizedName, UUID locationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select location from InventoryLocation location "
            + "where location.id = :locationId and location.business.id = :businessId")
    Optional<InventoryLocation> findByIdAndBusinessForUpdate(
            @Param("locationId") UUID locationId,
            @Param("businessId") UUID businessId);
}
