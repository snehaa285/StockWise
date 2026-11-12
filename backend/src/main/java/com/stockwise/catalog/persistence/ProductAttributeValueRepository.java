package com.stockwise.catalog.persistence;

import com.stockwise.catalog.domain.ProductAttributeValue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAttributeValueRepository
        extends JpaRepository<ProductAttributeValue, UUID> {
    List<ProductAttributeValue> findAllByProduct_Id(UUID productId);

    List<ProductAttributeValue> findAllByProduct_IdAndDefinition_ArchivedAtIsNull(
            UUID productId);

    boolean existsByProduct_Id(UUID productId);

    boolean existsByDefinition_Id(UUID definitionId);
}
