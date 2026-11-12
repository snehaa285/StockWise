package com.stockwise.catalog.application;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.application.BusinessConflictException;
import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.catalog.api.ProductResponse;
import com.stockwise.catalog.api.SaveProductRequest;
import com.stockwise.catalog.domain.Product;
import com.stockwise.catalog.domain.ProductCategory;
import com.stockwise.catalog.persistence.ProductAttributeValueRepository;
import com.stockwise.catalog.persistence.ProductRepository;
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
public class ProductService {
    private final BusinessAccessService businessAccessService;
    private final CategoryService categoryService;
    private final ProductRepository productRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductAttributeValueManager valueManager;
    private final InventoryCatalogGuard inventoryCatalogGuard;
    private final ExcelSyncQueueService syncQueueService;

    public ProductService(
            BusinessAccessService businessAccessService,
            CategoryService categoryService,
            ProductRepository productRepository,
            ProductAttributeValueRepository valueRepository,
            ProductAttributeValueManager valueManager,
            InventoryCatalogGuard inventoryCatalogGuard,
            ExcelSyncQueueService syncQueueService) {
        this.businessAccessService = businessAccessService;
        this.categoryService = categoryService;
        this.productRepository = productRepository;
        this.valueRepository = valueRepository;
        this.valueManager = valueManager;
        this.inventoryCatalogGuard = inventoryCatalogGuard;
        this.syncQueueService = syncQueueService;
    }

    @Transactional
    public ProductResponse create(
            UUID businessId, UUID actorId, SaveProductRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        ProductCategory category = categoryService.lockCategory(
                businessId, request.categoryId(), true);
        validateTrackingConfiguration(category, request);
        ProductFields fields = fields(businessId, request, null);
        Product product = productRepository.saveAndFlush(Product.create(
                category.getBusiness(),
                category,
                fields.name(),
                fields.normalizedName(),
                fields.sku(),
                fields.normalizedSku(),
                fields.unitOfMeasure(),
                fields.description(),
                request.batchTrackingOverride(),
                request.expiryTrackingOverride()));
        valueManager.replaceActiveValues(product, request.attributes());
        productRepository.flush();
        syncQueueService.enqueueChange(businessId, "PRODUCT");
        return response(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(UUID businessId, UUID actorId, UUID categoryId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        List<Product> products;
        if (categoryId == null) {
            products = productRepository
                    .findAllByBusiness_IdAndArchivedAtIsNullOrderByNameAsc(businessId);
        } else {
            categoryService.requireCategory(businessId, categoryId, true);
            products = productRepository
                    .findAllByBusiness_IdAndCategory_IdAndArchivedAtIsNullOrderByNameAsc(
                            businessId, categoryId);
        }
        return products.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID businessId, UUID actorId, UUID productId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return response(requireProduct(businessId, productId, true));
    }

    @Transactional
    public ProductResponse update(
            UUID businessId, UUID actorId, UUID productId, SaveProductRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        Product product = productRepository
                .findByIdAndBusinessForUpdate(productId, businessId)
                .filter(Product::isActive)
                .orElseThrow(BusinessNotFoundException::new);
        ProductCategory category = categoryService.lockCategory(
                businessId, request.categoryId(), true);
        validateTrackingConfiguration(category, request);
        if (hasTrackingConfigurationChanged(
                        product, category, request)
                && inventoryCatalogGuard.hasInventoryHistory(businessId, productId)) {
            throw new BusinessConflictException(
                    "Product tracking cannot change after inventory history exists.");
        }
        if (!product.getCategory().getId().equals(category.getId())
                && valueRepository.existsByProduct_Id(productId)) {
            throw new BusinessConflictException(
                    "A product with attribute values cannot change category.");
        }
        ProductFields fields = fields(businessId, request, productId);
        product.update(
                category,
                fields.name(),
                fields.normalizedName(),
                fields.sku(),
                fields.normalizedSku(),
                fields.unitOfMeasure(),
                fields.description(),
                request.batchTrackingOverride(),
                request.expiryTrackingOverride());
        productRepository.flush();
        valueManager.replaceActiveValues(product, request.attributes());
        productRepository.flush();
        syncQueueService.enqueueChange(businessId, "PRODUCT");
        return response(product);
    }

    @Transactional
    public void archive(UUID businessId, UUID actorId, UUID productId) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        Product product = productRepository
                .findByIdAndBusinessForUpdate(productId, businessId)
                .filter(Product::isActive)
                .orElseThrow(BusinessNotFoundException::new);
        if (inventoryCatalogGuard.hasPositiveStock(businessId, productId)) {
            throw new BusinessConflictException(
                    "A product with positive stock cannot be archived.");
        }
        product.archive(LocalDateTime.now(ZoneOffset.UTC));
        syncQueueService.enqueueChange(businessId, "PRODUCT");
    }

    private Product requireProduct(UUID businessId, UUID productId, boolean activeOnly) {
        return (activeOnly
                        ? productRepository.findByIdAndBusiness_IdAndArchivedAtIsNull(
                                productId, businessId)
                        : productRepository.findByIdAndBusiness_Id(productId, businessId))
                .orElseThrow(BusinessNotFoundException::new);
    }

    private ProductFields fields(
            UUID businessId, SaveProductRequest request, UUID currentProductId) {
        String name = CatalogNameNormalizer.displayName(request.name());
        String normalizedName = CatalogNameNormalizer.normalized(name);
        String sku = request.sku() == null || request.sku().isBlank()
                ? null : CatalogNameNormalizer.displayName(request.sku());
        String normalizedSku = sku == null ? null : CatalogNameNormalizer.normalizedSku(sku);
        if (normalizedSku != null) {
            boolean duplicate = currentProductId == null
                    ? productRepository.existsByBusiness_IdAndNormalizedSkuAndArchivedAtIsNull(
                            businessId, normalizedSku)
                    : productRepository
                            .existsByBusiness_IdAndNormalizedSkuAndArchivedAtIsNullAndIdNot(
                                    businessId, normalizedSku, currentProductId);
            if (duplicate) {
                throw new BusinessConflictException(
                        "An active product with that SKU already exists.");
            }
        }
        String unit = request.unitOfMeasure().trim();
        if (unit.isEmpty()) {
            throw new CatalogValidationException(
                    "unitOfMeasure", "Unit of measure cannot be empty.");
        }
        return new ProductFields(
                name,
                normalizedName,
                sku,
                normalizedSku,
                unit,
                request.description() == null || request.description().isBlank()
                        ? null : request.description().trim());
    }

    private void validateTrackingConfiguration(
            ProductCategory category, SaveProductRequest request) {
        boolean batchTracked = request.batchTrackingOverride() == null
                ? category.isBatchTrackingDefault() : request.batchTrackingOverride();
        boolean expiryTracked = request.expiryTrackingOverride() == null
                ? category.isExpiryTrackingDefault() : request.expiryTrackingOverride();
        if (expiryTracked && !batchTracked) {
            throw new CatalogValidationException(
                    "expiryTrackingOverride", "Expiry tracking requires batch tracking.");
        }
    }

    private boolean hasTrackingConfigurationChanged(
            Product product, ProductCategory category, SaveProductRequest request) {
        boolean currentBatch = product.getBatchTrackingOverride() == null
                ? product.getCategory().isBatchTrackingDefault()
                : product.getBatchTrackingOverride();
        boolean currentExpiry = product.getExpiryTrackingOverride() == null
                ? product.getCategory().isExpiryTrackingDefault()
                : product.getExpiryTrackingOverride();
        boolean requestedBatch = request.batchTrackingOverride() == null
                ? category.isBatchTrackingDefault()
                : request.batchTrackingOverride();
        boolean requestedExpiry = request.expiryTrackingOverride() == null
                ? category.isExpiryTrackingDefault()
                : request.expiryTrackingOverride();
        return currentBatch != requestedBatch || currentExpiry != requestedExpiry;
    }

    private ProductResponse response(Product product) {
        return ProductResponse.from(
                product, valueManager.valuesForProduct(product.getId()));
    }

    private record ProductFields(
            String name,
            String normalizedName,
            String sku,
            String normalizedSku,
            String unitOfMeasure,
            String description) {
    }
}
