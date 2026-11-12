package com.stockwise.catalog.persistence;

import com.stockwise.catalog.domain.ProductAttributeSelection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAttributeSelectionRepository
        extends JpaRepository<ProductAttributeSelection, UUID> {
    List<ProductAttributeSelection> findAllByValue_IdOrderByOption_PositionAsc(
            UUID valueId);

    void deleteAllByValue_Id(UUID valueId);

    boolean existsByOption_Id(UUID optionId);
}
