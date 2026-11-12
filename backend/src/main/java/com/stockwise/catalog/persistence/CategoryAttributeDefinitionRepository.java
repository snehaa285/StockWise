package com.stockwise.catalog.persistence;

import com.stockwise.catalog.domain.CategoryAttributeDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryAttributeDefinitionRepository
        extends JpaRepository<CategoryAttributeDefinition, UUID> {
    List<CategoryAttributeDefinition> findAllByBusiness_IdAndCategory_IdAndArchivedAtIsNullOrderByNameAsc(
            UUID businessId, UUID categoryId);

    List<CategoryAttributeDefinition> findAllByBusiness_IdAndCategory_IdOrderByNameAsc(
            UUID businessId, UUID categoryId);

    Optional<CategoryAttributeDefinition> findByIdAndBusiness_IdAndCategory_Id(
            UUID id, UUID businessId, UUID categoryId);

    boolean existsByCategory_IdAndNormalizedNameAndArchivedAtIsNull(
            UUID categoryId, String normalizedName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select definition from CategoryAttributeDefinition definition "
            + "where definition.id = :definitionId and definition.business.id = :businessId "
            + "and definition.category.id = :categoryId")
    Optional<CategoryAttributeDefinition> findForUpdate(
            @Param("definitionId") UUID definitionId,
            @Param("businessId") UUID businessId,
            @Param("categoryId") UUID categoryId);
}
