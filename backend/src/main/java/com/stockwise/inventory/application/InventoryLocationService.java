package com.stockwise.inventory.application;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.application.BusinessConflictException;
import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.Business;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.inventory.api.InventoryLocationResponse;
import com.stockwise.inventory.api.SaveLocationRequest;
import com.stockwise.inventory.domain.InventoryLocation;
import com.stockwise.inventory.persistence.InventoryLocationRepository;
import com.stockwise.inventory.persistence.InventoryStockBalanceRepository;
import com.stockwise.excelsync.application.ExcelSyncQueueService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class InventoryLocationService {
    private final BusinessAccessService businessAccessService;
    private final BusinessRepository businessRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryStockBalanceRepository balanceRepository;
    private final ExcelSyncQueueService syncQueueService;

    public InventoryLocationService(
            BusinessAccessService businessAccessService,
            BusinessRepository businessRepository,
            InventoryLocationRepository locationRepository,
            InventoryStockBalanceRepository balanceRepository,
            ExcelSyncQueueService syncQueueService) {
        this.businessAccessService = businessAccessService;
        this.businessRepository = businessRepository;
        this.locationRepository = locationRepository;
        this.balanceRepository = balanceRepository;
        this.syncQueueService = syncQueueService;
    }

    @Transactional
    public InventoryLocationResponse create(
            UUID businessId, UUID actorId, SaveLocationRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        String name = InventoryNameNormalizer.display(request.name(), "name", 160);
        String normalized = InventoryNameNormalizer.normalized(name);
        if (locationRepository.existsByBusiness_IdAndNormalizedNameAndArchivedAtIsNull(
                businessId, normalized)) {
            throw new BusinessConflictException("An active location with that name already exists.");
        }
        InventoryLocation location = locationRepository.saveAndFlush(InventoryLocation.create(
                business, name, normalized,
                InventoryNameNormalizer.optionalDescription(request.description())));
        syncQueueService.enqueueChange(businessId, "LOCATION");
        return InventoryLocationResponse.from(location);
    }

    @Transactional(readOnly = true)
    public List<InventoryLocationResponse> list(UUID businessId, UUID actorId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return locationRepository
                .findAllByBusiness_IdAndArchivedAtIsNullOrderByNameAsc(businessId)
                .stream()
                .map(InventoryLocationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryLocationResponse get(UUID businessId, UUID actorId, UUID locationId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return InventoryLocationResponse.from(requireLocation(businessId, locationId, false));
    }

    @Transactional
    public InventoryLocationResponse update(
            UUID businessId,
            UUID actorId,
            UUID locationId,
            SaveLocationRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        InventoryLocation location = requireLocation(businessId, locationId, true);
        String name = InventoryNameNormalizer.display(request.name(), "name", 160);
        String normalized = InventoryNameNormalizer.normalized(name);
        boolean duplicate = locationRepository
                .existsByBusiness_IdAndNormalizedNameAndArchivedAtIsNullAndIdNot(
                        businessId, normalized, locationId);
        if (duplicate) {
            throw new BusinessConflictException("An active location with that name already exists.");
        }
        location.update(
                name,
                normalized,
                InventoryNameNormalizer.optionalDescription(request.description()));
        locationRepository.flush();
        syncQueueService.enqueueChange(businessId, "LOCATION");
        return InventoryLocationResponse.from(location);
    }

    @Transactional
    public void archive(UUID businessId, UUID actorId, UUID locationId) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        InventoryLocation location = requireLocation(businessId, locationId, true);
        if (balanceRepository.existsByBusiness_IdAndLocation_IdAndQuantityGreaterThan(
                businessId, locationId, java.math.BigDecimal.ZERO)) {
            throw new BusinessConflictException(
                    "A location with stock cannot be archived.");
        }
        location.archive(LocalDateTime.now(ZoneOffset.UTC));
        syncQueueService.enqueueChange(businessId, "LOCATION");
    }

    @Transactional
    InventoryLocation lockActiveLocation(UUID businessId, UUID locationId) {
        return requireLocation(businessId, locationId, true);
    }

    private InventoryLocation requireLocation(
            UUID businessId, UUID locationId, boolean lock) {
        InventoryLocation location = (lock
                        ? locationRepository.findByIdAndBusinessForUpdate(locationId, businessId)
                        : locationRepository.findByIdAndBusiness_IdAndArchivedAtIsNull(
                                locationId, businessId))
                .orElseThrow(BusinessNotFoundException::new);
        if (!location.isActive()) {
            throw new BusinessNotFoundException();
        }
        return location;
    }
}
