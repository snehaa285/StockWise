package com.stockwise.inventory.application;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.catalog.domain.Product;
import com.stockwise.catalog.persistence.ProductRepository;
import com.stockwise.inventory.api.InventoryReorderLevelResponse;
import com.stockwise.inventory.api.SaveReorderLevelRequest;
import com.stockwise.inventory.domain.InventoryLocation;
import com.stockwise.inventory.domain.InventoryReorderLevel;
import com.stockwise.inventory.persistence.InventoryLocationRepository;
import com.stockwise.inventory.persistence.InventoryReorderLevelRepository;
import com.stockwise.excelsync.application.ExcelSyncQueueService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class InventoryReorderLevelService {
    private final BusinessAccessService businessAccessService;
    private final ProductRepository productRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryReorderLevelRepository reorderLevelRepository;
    private final ExcelSyncQueueService syncQueueService;

    public InventoryReorderLevelService(
            BusinessAccessService businessAccessService,
            ProductRepository productRepository,
            InventoryLocationRepository locationRepository,
            InventoryReorderLevelRepository reorderLevelRepository,
            ExcelSyncQueueService syncQueueService) {
        this.businessAccessService = businessAccessService;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.reorderLevelRepository = reorderLevelRepository;
        this.syncQueueService = syncQueueService;
    }

    @Transactional(readOnly = true)
    public List<InventoryReorderLevelResponse> list(UUID businessId, UUID actorId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return reorderLevelRepository.findAllForBusiness(businessId).stream()
                .map(InventoryReorderLevelResponse::from)
                .toList();
    }

    @Transactional
    public InventoryReorderLevelResponse save(
            UUID businessId, UUID actorId, SaveReorderLevelRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        BigDecimal value = reorderLevel(request.reorderLevel());
        Product product = productRepository
                .findByIdAndBusinessForUpdate(request.productId(), businessId)
                .filter(Product::isActive)
                .orElseThrow(BusinessNotFoundException::new);
        InventoryLocation location = locationRepository
                .findByIdAndBusinessForUpdate(request.locationId(), businessId)
                .filter(InventoryLocation::isActive)
                .orElseThrow(BusinessNotFoundException::new);

        InventoryReorderLevel level = reorderLevelRepository
                .findByBusiness_IdAndProduct_IdAndLocation_Id(
                        businessId, product.getId(), location.getId())
                .orElseGet(() -> InventoryReorderLevel.create(
                        location.getBusiness(), product, location, value));
        level.update(value);
        InventoryReorderLevelResponse response = InventoryReorderLevelResponse.from(
                reorderLevelRepository.saveAndFlush(level));
        syncQueueService.enqueueChange(businessId, "REORDER_LEVEL");
        return response;
    }

    @Transactional
    public void delete(
            UUID businessId, UUID actorId, UUID productId, UUID locationId) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        InventoryReorderLevel level = reorderLevelRepository
                .findByBusiness_IdAndProduct_IdAndLocation_Id(
                        businessId, productId, locationId)
                .orElseThrow(BusinessNotFoundException::new);
        reorderLevelRepository.delete(level);
        syncQueueService.enqueueChange(businessId, "REORDER_LEVEL");
    }

    private BigDecimal reorderLevel(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new InventoryValidationException(
                    "reorderLevel", "Reorder level must be zero or greater.");
        }
        BigDecimal scaled;
        try {
            scaled = value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InventoryValidationException(
                    "reorderLevel", "Reorder level supports at most three fractional digits.");
        }
        int integerDigits = Math.max(0, scaled.precision() - scaled.scale());
        if (integerDigits > 15) {
            throw new InventoryValidationException(
                    "reorderLevel", "Reorder level exceeds the supported quantity range.");
        }
        return scaled;
    }
}
