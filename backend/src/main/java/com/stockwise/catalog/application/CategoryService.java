package com.stockwise.catalog.application;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.application.BusinessConflictException;
import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.Business;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.catalog.api.CategoryResponse;
import com.stockwise.catalog.api.SaveCategoryRequest;
import com.stockwise.catalog.domain.CategoryAttributeDefinition;
import com.stockwise.catalog.domain.ProductCategory;
import com.stockwise.catalog.persistence.CategoryAttributeDefinitionRepository;
import com.stockwise.catalog.persistence.CategoryAttributeOptionRepository;
import com.stockwise.catalog.persistence.ProductCategoryRepository;
import com.stockwise.catalog.persistence.ProductRepository;
import com.stockwise.excelsync.application.ExcelSyncQueueService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class CategoryService {
    private final BusinessAccessService businessAccessService;
    private final BusinessRepository businessRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryCatalogGuard inventoryCatalogGuard;
    private final CategoryAttributeDefinitionRepository definitionRepository;
    private final CategoryAttributeOptionRepository optionRepository;
    private final EntityManager entityManager;
    private final ExcelSyncQueueService syncQueueService;

    public CategoryService(
            BusinessAccessService businessAccessService,
            BusinessRepository businessRepository,
            ProductCategoryRepository categoryRepository,
            ProductRepository productRepository,
            InventoryCatalogGuard inventoryCatalogGuard,
            CategoryAttributeDefinitionRepository definitionRepository,
            CategoryAttributeOptionRepository optionRepository,
            EntityManager entityManager,
            ExcelSyncQueueService syncQueueService) {
        this.businessAccessService = businessAccessService;
        this.businessRepository = businessRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.inventoryCatalogGuard = inventoryCatalogGuard;
        this.definitionRepository = definitionRepository;
        this.optionRepository = optionRepository;
        this.entityManager = entityManager;
        this.syncQueueService = syncQueueService;
    }

    @Transactional
    public CategoryResponse create(UUID businessId, UUID actorId, SaveCategoryRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        validateTrackingDefaults(
                request.batchTrackingDefault(), request.expiryTrackingDefault());
        String name = CatalogNameNormalizer.displayName(request.name());
        String normalizedName = CatalogNameNormalizer.normalized(name);
        ensureUniqueName(businessId, normalizedName, null);
        ProductCategory category = categoryRepository.saveAndFlush(ProductCategory.create(
                business,
                name,
                normalizedName,
                normalizeDescription(request.description()),
                request.batchTrackingDefault(),
                request.expiryTrackingDefault()));
        syncQueueService.enqueueChange(businessId, "CATEGORY");
        return CategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID businessId, UUID actorId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return categoryRepository
                .findAllByBusiness_IdOrderByNameAsc(businessId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID businessId, UUID actorId, UUID categoryId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return CategoryResponse.from(requireCategory(businessId, categoryId, true));
    }

    @Transactional
    public CategoryResponse update(
            UUID businessId,
            UUID actorId,
            UUID categoryId,
            SaveCategoryRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        ProductCategory category = lockCategory(businessId, categoryId, true);
        String name = CatalogNameNormalizer.displayName(request.name());
        String normalizedName = CatalogNameNormalizer.normalized(name);
        ensureUniqueName(businessId, normalizedName, categoryId);
        validateTrackingDefaults(
                request.batchTrackingDefault(), request.expiryTrackingDefault());
        boolean invalidExistingProducts = productRepository
                .findAllByBusiness_IdAndCategory_IdOrderByNameAsc(businessId, categoryId)
                .stream()
                .anyMatch(product -> {
                    boolean newBatch = effectiveBatchTracking(
                            product.getBatchTrackingOverride(),
                            request.batchTrackingDefault());
                    boolean newExpiry = effectiveExpiryTracking(
                            product.getExpiryTrackingOverride(),
                            request.expiryTrackingDefault());
                    boolean invalid = newExpiry && !newBatch;
                    boolean trackingChanged = effectiveBatchTracking(
                                    product.getBatchTrackingOverride(),
                                    category.isBatchTrackingDefault()) != newBatch
                            || effectiveExpiryTracking(
                                    product.getExpiryTrackingOverride(),
                                    category.isExpiryTrackingDefault()) != newExpiry;
                    return invalid || (trackingChanged
                            && inventoryCatalogGuard.hasInventoryHistory(
                                    businessId, product.getId()));
                });
        if (invalidExistingProducts) {
            throw new BusinessConflictException(
                    "Category defaults conflict with existing product tracking or inventory history.");
        }
        category.update(
                name,
                normalizedName,
                normalizeDescription(request.description()),
                request.batchTrackingDefault(),
                request.expiryTrackingDefault());
        categoryRepository.flush();
        syncQueueService.enqueueChange(businessId, "CATEGORY");
        return CategoryResponse.from(category);
    }

    @Transactional
    public void archive(UUID businessId, UUID actorId, UUID categoryId) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        ProductCategory category = lockCategory(businessId, categoryId, true);
        category.archive(LocalDateTime.now(ZoneOffset.UTC));
        syncQueueService.enqueueChange(businessId, "CATEGORY");
    }

    @Transactional
    public void delete(UUID businessId, UUID actorId, UUID categoryId) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        ProductCategory category = lockCategory(businessId, categoryId, false);
        if (productRepository.existsByCategory_Id(categoryId)) {
            throw new BusinessConflictException(
                    "This category is used by products and cannot be deleted. Archive it instead.");
        }

        List<CategoryAttributeDefinition> definitions =
                definitionRepository.findAllByBusiness_IdAndCategory_IdOrderByNameAsc(
                        businessId, categoryId);
        for (CategoryAttributeDefinition definition : definitions) {
            optionRepository.deleteAllInBatch(
                    optionRepository.findAllByDefinition_IdOrderByPositionAscLabelAsc(
                            definition.getId()));
        }
        definitionRepository.deleteAllInBatch(definitions);
        entityManager.flush();
        entityManager.clear();
        categoryRepository.deleteById(categoryId);
        syncQueueService.enqueueChange(businessId, "CATEGORY");
    }

    @Transactional(readOnly = true)
    public ProductCategory requireCategory(UUID businessId, UUID categoryId, boolean activeOnly) {
        return (activeOnly
                        ? categoryRepository.findByIdAndBusiness_IdAndArchivedAtIsNull(
                                categoryId, businessId)
                        : categoryRepository.findByIdAndBusiness_Id(categoryId, businessId))
                .orElseThrow(BusinessNotFoundException::new);
    }

    @Transactional
    public ProductCategory lockCategory(UUID businessId, UUID categoryId, boolean activeOnly) {
        ProductCategory category = categoryRepository
                .findByIdAndBusinessForUpdate(categoryId, businessId)
                .orElseThrow(BusinessNotFoundException::new);
        if (activeOnly && !category.isActive()) {
            throw new BusinessNotFoundException();
        }
        return category;
    }

    private void ensureUniqueName(UUID businessId, String normalizedName, UUID exceptId) {
        boolean exists = categoryRepository
                .existsByBusiness_IdAndNormalizedNameAndArchivedAtIsNull(
                        businessId, normalizedName);
        if (exists && (exceptId == null
                || categoryRepository.findByIdAndBusiness_IdAndArchivedAtIsNull(
                        exceptId, businessId)
                        .map(category -> !category.getNormalizedName().equals(normalizedName))
                        .orElse(true))) {
            throw new BusinessConflictException(
                    "An active category with that name already exists.");
        }
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private void validateTrackingDefaults(boolean batchTracking, boolean expiryTracking) {
        if (expiryTracking && !batchTracking) {
            throw new CatalogValidationException(
                    "expiryTrackingDefault", "Expiry tracking requires batch tracking.");
        }
    }

    private boolean effectiveBatchTracking(Boolean override, boolean categoryDefault) {
        return override == null ? categoryDefault : override;
    }

    private boolean effectiveExpiryTracking(Boolean override, boolean categoryDefault) {
        return override == null ? categoryDefault : override;
    }
}
