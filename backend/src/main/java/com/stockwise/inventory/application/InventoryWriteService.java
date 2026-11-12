package com.stockwise.inventory.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.application.BusinessConflictException;
import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.Business;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.catalog.domain.Product;
import com.stockwise.catalog.domain.ProductCategory;
import com.stockwise.catalog.persistence.ProductCategoryRepository;
import com.stockwise.catalog.persistence.ProductRepository;
import com.stockwise.inventory.api.AdjustStockRequest;
import com.stockwise.inventory.api.AdjustmentDirection;
import com.stockwise.inventory.api.InventoryBatchResponse;
import com.stockwise.inventory.api.InventoryMovementPageResponse;
import com.stockwise.inventory.api.InventoryMovementResponse;
import com.stockwise.inventory.api.InventoryOperationResponse;
import com.stockwise.inventory.api.InventoryStockBalanceResponse;
import com.stockwise.inventory.api.ReceiveStockRequest;
import com.stockwise.inventory.api.TransferStockRequest;
import com.stockwise.inventory.domain.InventoryBatch;
import com.stockwise.inventory.domain.InventoryLocation;
import com.stockwise.inventory.domain.InventoryMovement;
import com.stockwise.inventory.domain.InventoryOperation;
import com.stockwise.inventory.domain.InventoryStockBalance;
import com.stockwise.inventory.persistence.InventoryBatchRepository;
import com.stockwise.inventory.persistence.InventoryLocationRepository;
import com.stockwise.inventory.persistence.InventoryMovementRepository;
import com.stockwise.inventory.persistence.InventoryOperationRepository;
import com.stockwise.inventory.persistence.InventoryStockBalanceRepository;
import com.stockwise.excelsync.application.ExcelSyncQueueService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class InventoryWriteService {
    private final BusinessAccessService businessAccessService;
    private final BusinessRepository businessRepository;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryStockBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryOperationRepository operationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ExcelSyncQueueService syncQueueService;

    public InventoryWriteService(
            BusinessAccessService businessAccessService,
            BusinessRepository businessRepository,
            AppUserRepository appUserRepository,
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            InventoryLocationRepository locationRepository,
            InventoryBatchRepository batchRepository,
            InventoryStockBalanceRepository balanceRepository,
            InventoryMovementRepository movementRepository,
            InventoryOperationRepository operationRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ExcelSyncQueueService syncQueueService) {
        this.businessAccessService = businessAccessService;
        this.businessRepository = businessRepository;
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.batchRepository = batchRepository;
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
        this.operationRepository = operationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.syncQueueService = syncQueueService;
    }

    @Transactional
    public InventoryOperationResponse receive(
            UUID businessId, UUID actorId, String idempotencyKey, ReceiveStockRequest request) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        BigDecimal quantity = quantity(request.quantity());
        String key = idempotencyKey(idempotencyKey);
        String batchCode = optionalBatchCode(request.batchCode());
        String canonical = canonical(
                "RECEIPT", request.productId(), request.locationId(), quantity,
                normalizedBatchCode(batchCode), request.expiryDate());
        byte[] requestHash = hash(canonical);
        InventoryOperation operation = beginOperation(
                businessId, actorId, "RECEIPT", key, requestHash);
        InventoryOperationResponse replay = replayIfCompleted(operation, requestHash);
        if (replay != null) {
            return replay;
        }

        Product product = lockActiveProduct(businessId, request.productId());
        InventoryLocation location = lockActiveLocation(businessId, request.locationId());
        ProductTracking tracking = tracking(product);
        InventoryBatch batch = resolveInboundBatch(
                businessId, product, tracking, batchCode, request.expiryDate());
        InventoryStockBalance balance = lockOrCreateBalance(
                businessId, product, location, batch);
        BigDecimal after = balance.add(quantity);
        balanceRepository.save(balance);
        InventoryMovement movement = saveMovement(
                operation, product, location, batch, "RECEIPT",
                quantity, after, null, null, null, actorId);
        InventoryOperationResponse response = completeOperation(operation, key, List.of(movement));
        syncQueueService.enqueueChange(businessId, "INVENTORY");
        return response;
    }

    @Transactional
    public InventoryOperationResponse adjust(
            UUID businessId, UUID actorId, String idempotencyKey, AdjustStockRequest request) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        BigDecimal quantity = quantity(request.quantity());
        String key = idempotencyKey(idempotencyKey);
        String reason = requiredReason(request.reason());
        String batchCode = optionalBatchCode(request.batchCode());
        String canonical = canonical(
                "ADJUSTMENT", request.productId(), request.locationId(),
                request.direction(), quantity, normalizedBatchCode(batchCode),
                request.expiryDate(), reason);
        byte[] requestHash = hash(canonical);
        InventoryOperation operation = beginOperation(
                businessId, actorId, "ADJUSTMENT", key, requestHash);
        InventoryOperationResponse replay = replayIfCompleted(operation, requestHash);
        if (replay != null) {
            return replay;
        }

        Product product = lockActiveProduct(businessId, request.productId());
        InventoryLocation location = lockActiveLocation(businessId, request.locationId());
        ProductTracking tracking = tracking(product);
        InventoryBatch batch = request.direction() == AdjustmentDirection.INCREASE
                ? resolveInboundBatch(
                        businessId, product, tracking, batchCode, request.expiryDate())
                : resolveOutboundBatch(
                        businessId, product, tracking, batchCode, request.expiryDate());
        InventoryStockBalance balance = lockOrCreateBalance(
                businessId, product, location, batch);
        BigDecimal delta = request.direction() == AdjustmentDirection.INCREASE
                ? quantity : quantity.negate();
        BigDecimal after = applyDelta(balance, delta);
        balanceRepository.save(balance);
        InventoryMovement movement = saveMovement(
                operation,
                product,
                location,
                batch,
                request.direction() == AdjustmentDirection.INCREASE
                        ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT",
                delta,
                after,
                null,
                null,
                reason,
                actorId);
        InventoryOperationResponse response = completeOperation(operation, key, List.of(movement));
        syncQueueService.enqueueChange(businessId, "INVENTORY");
        return response;
    }

    @Transactional
    public InventoryOperationResponse transfer(
            UUID businessId, UUID actorId, String idempotencyKey, TransferStockRequest request) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        BigDecimal quantity = quantity(request.quantity());
        String key = idempotencyKey(idempotencyKey);
        String reason = optionalReason(request.reason());
        String batchCode = optionalBatchCode(request.batchCode());
        String canonical = canonical(
                "TRANSFER", request.productId(), request.sourceLocationId(),
                request.destinationLocationId(), quantity, normalizedBatchCode(batchCode), reason);
        byte[] requestHash = hash(canonical);
        InventoryOperation operation = beginOperation(
                businessId, actorId, "TRANSFER", key, requestHash);
        InventoryOperationResponse replay = replayIfCompleted(operation, requestHash);
        if (replay != null) {
            return replay;
        }

        if (request.sourceLocationId().equals(request.destinationLocationId())) {
            throw new InventoryValidationException(
                    "destinationLocationId", "Source and destination must be different.");
        }
        Product product = lockActiveProduct(businessId, request.productId());
        List<UUID> locationIds = new ArrayList<>(
                List.of(request.sourceLocationId(), request.destinationLocationId()));
        locationIds.sort(UUID::compareTo);
        InventoryLocation first = lockActiveLocation(businessId, locationIds.get(0));
        InventoryLocation second = lockActiveLocation(businessId, locationIds.get(1));
        InventoryLocation source = first.getId().equals(request.sourceLocationId())
                ? first : second;
        InventoryLocation destination = first.getId().equals(request.destinationLocationId())
                ? first : second;

        ProductTracking tracking = tracking(product);
        InventoryBatch batch = resolveOutboundBatch(
                businessId, product, tracking, batchCode, null);
        InventoryStockBalance sourceBalance = lockOrCreateBalance(
                businessId, product, source, batch);
        InventoryStockBalance destinationBalance = lockOrCreateBalance(
                businessId, product, destination, batch);
        BigDecimal sourceAfter = applyDelta(sourceBalance, quantity.negate());
        BigDecimal destinationAfter = applyDelta(destinationBalance, quantity);
        balanceRepository.saveAll(List.of(sourceBalance, destinationBalance));
        List<InventoryMovement> movements = movementRepository.saveAllAndFlush(List.of(
                InventoryMovement.create(
                        source.getBusiness(),
                        operation,
                        product,
                        source,
                        batch,
                        "TRANSFER_OUT",
                        quantity.negate(),
                        sourceAfter,
                        source,
                        destination,
                        reason,
                        requireActor(actorId)),
                InventoryMovement.create(
                        destination.getBusiness(),
                        operation,
                        product,
                        destination,
                        batch,
                        "TRANSFER_IN",
                        quantity,
                        destinationAfter,
                        source,
                        destination,
                        reason,
                        requireActor(actorId))));
        InventoryOperationResponse response = completeOperation(operation, key, movements);
        syncQueueService.enqueueChange(businessId, "INVENTORY");
        return response;
    }

    @Transactional(readOnly = true)
    public List<InventoryStockBalanceResponse> stock(
            UUID businessId, UUID actorId, UUID productId, UUID locationId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        if (productId != null) {
            productRepository.findByIdAndBusiness_Id(productId, businessId)
                    .orElseThrow(BusinessNotFoundException::new);
        }
        if (locationId != null) {
            locationRepository.findByIdAndBusiness_IdAndArchivedAtIsNull(locationId, businessId)
                    .orElseThrow(BusinessNotFoundException::new);
        }
        return balanceRepository.findPositiveBalances(businessId, productId, locationId)
                .stream()
                .map(InventoryStockBalanceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryBatchResponse> batches(
            UUID businessId, UUID actorId, UUID productId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        productRepository.findByIdAndBusiness_Id(productId, businessId)
                .orElseThrow(BusinessNotFoundException::new);
        return batchRepository
                .findAllByBusiness_IdAndProduct_IdOrderByCreatedAtDesc(businessId, productId)
                .stream()
                .map(InventoryBatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryMovementPageResponse movements(
            UUID businessId,
            UUID actorId,
            UUID productId,
            UUID locationId,
            int page,
            int size) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        if (page < 0 || size < 1 || size > 200) {
            throw new InventoryValidationException("page", "Invalid movement page or size.");
        }
        Page<InventoryMovement> results = movementRepository.findHistory(
                businessId,
                productId,
                locationId,
                PageRequest.of(page, size, Sort.unsorted()));
        return new InventoryMovementPageResponse(
                results.getContent().stream().map(InventoryMovementResponse::from).toList(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages());
    }

    private InventoryOperation beginOperation(
            UUID businessId,
            UUID actorId,
            String operationType,
            String key,
            byte[] requestHash) {
        UUID operationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO inventory_operation "
                        + "(id, business_id, operation_type, idempotency_key, request_hash, "
                        + "created_by_user_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6)) "
                        + "ON DUPLICATE KEY UPDATE id = id",
                uuidBytes(operationId),
                uuidBytes(businessId),
                operationType,
                key,
                requestHash,
                uuidBytes(actorId));
        return operationRepository.findByBusinessAndKeyForUpdate(businessId, key)
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory operation was not persisted."));
    }

    private InventoryOperationResponse replayIfCompleted(
            InventoryOperation operation, byte[] requestHash) {
        if (!MessageDigest.isEqual(operation.getRequestHash(), requestHash)) {
            throw new BusinessConflictException(
                    "Idempotency key was already used for a different request.");
        }
        if (operation.getResultJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    operation.getResultJson(), InventoryOperationResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored inventory operation response could not be read.", exception);
        }
    }

    private InventoryOperationResponse completeOperation(
            InventoryOperation operation, String key, List<InventoryMovement> movements) {
        InventoryOperationResponse response = new InventoryOperationResponse(
                operation.getId(),
                operation.getOperationType(),
                key,
                movements.stream().map(InventoryMovementResponse::from).toList(),
                operation.getCreatedAt());
        try {
            operation.complete(objectMapper.writeValueAsString(response));
            operationRepository.saveAndFlush(operation);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Inventory operation response could not be stored.", exception);
        }
        return response;
    }

    private Product lockActiveProduct(UUID businessId, UUID productId) {
        Product product = productRepository.findByIdAndBusinessForUpdate(productId, businessId)
                .filter(Product::isActive)
                .orElseThrow(BusinessNotFoundException::new);
        ProductCategory category = categoryRepository
                .findByIdAndBusinessForUpdate(product.getCategory().getId(), businessId)
                .filter(ProductCategory::isActive)
                .orElseThrow(BusinessNotFoundException::new);
        if (!category.isActive()) {
            throw new BusinessNotFoundException();
        }
        return product;
    }

    private InventoryLocation lockActiveLocation(UUID businessId, UUID locationId) {
        InventoryLocation location = locationRepository
                .findByIdAndBusinessForUpdate(locationId, businessId)
                .orElseThrow(BusinessNotFoundException::new);
        if (!location.isActive()) {
            throw new BusinessNotFoundException();
        }
        return location;
    }

    private InventoryBatch resolveInboundBatch(
            UUID businessId,
            Product product,
            ProductTracking tracking,
            String batchCode,
            LocalDate expiryDate) {
        if (!tracking.batchTracked()) {
            rejectBatchFields(batchCode, expiryDate);
            return null;
        }
        String displayCode = requiredBatchCode(batchCode);
        String normalizedCode = InventoryNameNormalizer.normalizedCode(displayCode);
        var existing = batchRepository.findByProductAndCodeForUpdate(
                businessId, product.getId(), normalizedCode);
        if (existing.isPresent()) {
            InventoryBatch batch = existing.get();
            validateExistingExpiry(batch, expiryDate, tracking.expiryTracked());
            return batch;
        }
        if (tracking.expiryTracked() && expiryDate == null) {
            throw new InventoryValidationException(
                    "expiryDate", "Expiry date is required for a new tracked batch.");
        }
        if (!tracking.expiryTracked() && expiryDate != null) {
            throw new InventoryValidationException(
                    "expiryDate", "Expiry date is not enabled for this product.");
        }
        Business business = businessRepository.findById(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        return batchRepository.saveAndFlush(InventoryBatch.create(
                business,
                product,
                displayCode,
                normalizedCode,
                expiryDate));
    }

    private InventoryBatch resolveOutboundBatch(
            UUID businessId,
            Product product,
            ProductTracking tracking,
            String batchCode,
            LocalDate expiryDate) {
        if (!tracking.batchTracked()) {
            rejectBatchFields(batchCode, expiryDate);
            return null;
        }
        String displayCode = requiredBatchCode(batchCode);
        InventoryBatch batch = batchRepository.findByProductAndCodeForUpdate(
                        businessId,
                        product.getId(),
                        InventoryNameNormalizer.normalizedCode(displayCode))
                .orElseThrow(BusinessNotFoundException::new);
        validateExistingExpiry(batch, expiryDate, tracking.expiryTracked());
        return batch;
    }

    private void validateExistingExpiry(
            InventoryBatch batch, LocalDate requestedExpiry, boolean expiryTracked) {
        if (!expiryTracked && requestedExpiry != null) {
            throw new InventoryValidationException(
                    "expiryDate", "Expiry date is not enabled for this product.");
        }
        if (requestedExpiry != null && !requestedExpiry.equals(batch.getExpiryDate())) {
            throw new BusinessConflictException(
                    "The batch code is already associated with a different expiry date.");
        }
        if (expiryTracked && batch.getExpiryDate() == null) {
            throw new BusinessConflictException(
                    "The existing batch has no expiry date but expiry tracking is enabled.");
        }
    }

    private void rejectBatchFields(String batchCode, LocalDate expiryDate) {
        if (batchCode != null || expiryDate != null) {
            throw new InventoryValidationException(
                    "batchCode", "Batch details are not enabled for this product.");
        }
    }

    private InventoryStockBalance lockOrCreateBalance(
            UUID businessId,
            Product product,
            InventoryLocation location,
            InventoryBatch batch) {
        return balanceRepository.findBucketForUpdate(
                        businessId,
                        product.getId(),
                        location.getId(),
                        batch == null ? null : batch.getId())
                .orElseGet(() -> balanceRepository.saveAndFlush(
                        InventoryStockBalance.create(
                                location.getBusiness(), product, location, batch)));
    }

    private InventoryMovement saveMovement(
            InventoryOperation operation,
            Product product,
            InventoryLocation location,
            InventoryBatch batch,
            String movementType,
            BigDecimal delta,
            BigDecimal quantityAfter,
            InventoryLocation source,
            InventoryLocation destination,
            String reason,
            UUID actorId) {
        return movementRepository.saveAndFlush(InventoryMovement.create(
                location.getBusiness(),
                operation,
                product,
                location,
                batch,
                movementType,
                delta,
                quantityAfter,
                source,
                destination,
                reason,
                requireActor(actorId)));
    }

    private BigDecimal applyDelta(InventoryStockBalance balance, BigDecimal delta) {
        if (balance.getQuantity().add(delta).signum() < 0) {
            throw new BusinessConflictException("Insufficient stock for this operation.");
        }
        return balance.add(delta);
    }

    private AppUser requireActor(UUID actorId) {
        return appUserRepository.findById(actorId)
                .orElseThrow(BusinessNotFoundException::new);
    }

    private ProductTracking tracking(Product product) {
        ProductCategory category = product.getCategory();
        boolean batchTracked = product.getBatchTrackingOverride() == null
                ? category.isBatchTrackingDefault()
                : product.getBatchTrackingOverride();
        boolean expiryTracked = product.getExpiryTrackingOverride() == null
                ? category.isExpiryTrackingDefault()
                : product.getExpiryTrackingOverride();
        if (expiryTracked && !batchTracked) {
            throw new BusinessConflictException(
                    "Product tracking configuration requires correction before stock operations.");
        }
        return new ProductTracking(batchTracked, expiryTracked);
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new InventoryValidationException("quantity", "Quantity must be positive.");
        }
        BigDecimal scaled;
        try {
            scaled = value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InventoryValidationException(
                    "quantity", "Quantity may have at most three decimal places.");
        }
        if (scaled.precision() - scaled.scale() > 15) {
            throw new InventoryValidationException(
                    "quantity", "Quantity exceeds the supported precision.");
        }
        return scaled;
    }

    private String idempotencyKey(String key) {
        if (key == null) {
            throw new InventoryValidationException(
                    "Idempotency-Key", "Idempotency-Key header is required.");
        }
        String normalized = key.trim();
        if (normalized.isEmpty()
                || normalized.length() > 128
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new InventoryValidationException(
                    "Idempotency-Key", "Idempotency-Key must contain 1 to 128 visible characters.");
        }
        return normalized;
    }

    private String requiredBatchCode(String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            throw new InventoryValidationException(
                    "batchCode", "Batch code is required for this product.");
        }
        return InventoryNameNormalizer.display(batchCode, "batchCode", 120);
    }

    private String optionalBatchCode(String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            return null;
        }
        return InventoryNameNormalizer.display(batchCode, "batchCode", 120);
    }

    private String normalizedBatchCode(String batchCode) {
        return batchCode == null ? null : InventoryNameNormalizer.normalizedCode(batchCode);
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InventoryValidationException("reason", "Reason is required.");
        }
        return InventoryNameNormalizer.display(reason, "reason", 500);
    }

    private String optionalReason(String reason) {
        return reason == null || reason.isBlank()
                ? null : InventoryNameNormalizer.display(reason, "reason", 500);
    }

    private byte[] hash(String canonicalRequest) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String canonical(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            String part;
            if (value instanceof BigDecimal decimal) {
                part = decimal.toPlainString();
            } else if (value instanceof Enum<?> enumValue) {
                part = enumValue.name();
            } else {
                part = value == null ? null : value.toString();
            }
            if (part == null) {
                builder.append("-1:");
            } else {
                builder.append(part.length()).append(':').append(part);
            }
        }
        return builder.toString();
    }

    private byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private record ProductTracking(boolean batchTracked, boolean expiryTracked) {
    }
}
