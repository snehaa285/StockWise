package com.stockwise.catalog.persistence;

import com.stockwise.catalog.domain.CategoryAttributeOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryAttributeOptionRepository
        extends JpaRepository<CategoryAttributeOption, UUID> {
    List<CategoryAttributeOption> findAllByDefinition_IdOrderByPositionAscLabelAsc(
            UUID definitionId);

    List<CategoryAttributeOption> findAllByDefinition_IdAndActiveTrueOrderByPositionAscLabelAsc(
            UUID definitionId);

    Optional<CategoryAttributeOption> findByIdAndDefinition_Id(UUID id, UUID definitionId);

    boolean existsByDefinition_IdAndNormalizedLabel(UUID definitionId, String normalizedLabel);
}
