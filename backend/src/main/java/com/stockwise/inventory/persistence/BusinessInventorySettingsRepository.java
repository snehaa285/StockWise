package com.stockwise.inventory.persistence;

import com.stockwise.inventory.domain.BusinessInventorySettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessInventorySettingsRepository
        extends JpaRepository<BusinessInventorySettings, UUID> {
    Optional<BusinessInventorySettings> findByBusiness_Id(UUID businessId);
}
