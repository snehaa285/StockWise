package com.stockwise.catalog.persistence;

import com.stockwise.catalog.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findAllByBusiness_IdOrderByNameAsc(UUID businessId);

    List<Product> findAllByBusiness_IdAndArchivedAtIsNullOrderByNameAsc(UUID businessId);

    List<Product> findAllByBusiness_IdAndCategory_IdAndArchivedAtIsNullOrderByNameAsc(
            UUID businessId, UUID categoryId);

    List<Product> findAllByBusiness_IdAndCategory_IdOrderByNameAsc(
            UUID businessId, UUID categoryId);

    Optional<Product> findByIdAndBusiness_IdAndArchivedAtIsNull(UUID id, UUID businessId);

    Optional<Product> findByIdAndBusiness_Id(UUID id, UUID businessId);

    boolean existsByBusiness_IdAndNormalizedSkuAndArchivedAtIsNull(
            UUID businessId, String normalizedSku);

    boolean existsByBusiness_IdAndNormalizedSkuAndArchivedAtIsNullAndIdNot(
            UUID businessId, String normalizedSku, UUID productId);

    boolean existsByCategory_IdAndArchivedAtIsNull(UUID categoryId);

    boolean existsByCategory_Id(UUID categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product "
            + "where product.id = :productId and product.business.id = :businessId")
    Optional<Product> findByIdAndBusinessForUpdate(
            @Param("productId") UUID productId,
            @Param("businessId") UUID businessId);
}
