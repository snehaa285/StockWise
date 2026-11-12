package com.stockwise.catalog.persistence;

import com.stockwise.catalog.domain.ProductCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    List<ProductCategory> findAllByBusiness_IdOrderByNameAsc(UUID businessId);

    List<ProductCategory> findAllByBusiness_IdAndArchivedAtIsNullOrderByNameAsc(UUID businessId);

    Optional<ProductCategory> findByIdAndBusiness_IdAndArchivedAtIsNull(
            UUID id, UUID businessId);

    Optional<ProductCategory> findByIdAndBusiness_Id(UUID id, UUID businessId);

    boolean existsByBusiness_IdAndNormalizedNameAndArchivedAtIsNull(
            UUID businessId, String normalizedName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select category from ProductCategory category "
            + "where category.id = :categoryId and category.business.id = :businessId")
    Optional<ProductCategory> findByIdAndBusinessForUpdate(
            @Param("categoryId") UUID categoryId,
            @Param("businessId") UUID businessId);
}
