package com.stockwise.catalog.application;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.application.BusinessConflictException;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.catalog.api.AttributeConstraintsRequest;
import com.stockwise.catalog.api.AttributeDefinitionResponse;
import com.stockwise.catalog.api.AttributeOptionRequest;
import com.stockwise.catalog.api.AttributeOptionResponse;
import com.stockwise.catalog.api.CreateAttributeDefinitionRequest;
import com.stockwise.catalog.api.UpdateAttributeDefinitionRequest;
import com.stockwise.catalog.domain.AttributeType;
import com.stockwise.catalog.domain.CategoryAttributeDefinition;
import com.stockwise.catalog.domain.CategoryAttributeOption;
import com.stockwise.catalog.domain.ProductCategory;
import com.stockwise.catalog.persistence.CategoryAttributeDefinitionRepository;
import com.stockwise.catalog.persistence.CategoryAttributeOptionRepository;
import com.stockwise.catalog.persistence.ProductAttributeSelectionRepository;
import com.stockwise.catalog.persistence.ProductAttributeValueRepository;
import com.stockwise.excelsync.application.ExcelSyncQueueService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class AttributeDefinitionService {
    private final BusinessAccessService businessAccessService;
    private final CategoryService categoryService;
    private final CategoryAttributeDefinitionRepository definitionRepository;
    private final CategoryAttributeOptionRepository optionRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductAttributeSelectionRepository selectionRepository;
    private final ExcelSyncQueueService syncQueueService;

    public AttributeDefinitionService(
            BusinessAccessService businessAccessService,
            CategoryService categoryService,
            CategoryAttributeDefinitionRepository definitionRepository,
            CategoryAttributeOptionRepository optionRepository,
            ProductAttributeValueRepository valueRepository,
            ProductAttributeSelectionRepository selectionRepository,
            ExcelSyncQueueService syncQueueService) {
        this.businessAccessService = businessAccessService;
        this.categoryService = categoryService;
        this.definitionRepository = definitionRepository;
        this.optionRepository = optionRepository;
        this.valueRepository = valueRepository;
        this.selectionRepository = selectionRepository;
        this.syncQueueService = syncQueueService;
    }

    @Transactional
    public AttributeDefinitionResponse create(
            UUID businessId,
            UUID actorId,
            UUID categoryId,
            CreateAttributeDefinitionRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        ProductCategory category = categoryService.lockCategory(businessId, categoryId, true);
        String name = CatalogNameNormalizer.displayName(request.name());
        String normalizedName = CatalogNameNormalizer.normalized(name);
        ensureUniqueName(categoryId, normalizedName, null);
        validateDefinition(
                request.type(), request.required(), request.constraints(), request.options());
        CategoryAttributeDefinition definition = definitionRepository.saveAndFlush(
                CategoryAttributeDefinition.create(
                        category.getBusiness(),
                        category,
                        name,
                        normalizedName,
                        request.type(),
                        request.required(),
                        normalizeDescription(request.description()),
                        request.constraints().minTextLength(),
                        request.constraints().maxTextLength(),
                        request.constraints().minInteger(),
                        request.constraints().maxInteger(),
                        request.constraints().minDecimal(),
                        request.constraints().maxDecimal(),
                        request.constraints().minDate(),
                        request.constraints().maxDate(),
                        request.constraints().minSelections(),
                        request.constraints().maxSelections()));
        saveOptions(definition, request.options());
        syncQueueService.enqueueChange(businessId, "PRODUCT_ATTRIBUTES");
        return response(definition);
    }

    @Transactional(readOnly = true)
    public List<AttributeDefinitionResponse> list(
            UUID businessId, UUID actorId, UUID categoryId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        categoryService.requireCategory(businessId, categoryId, true);
        return definitionRepository
                .findAllByBusiness_IdAndCategory_IdAndArchivedAtIsNullOrderByNameAsc(
                        businessId, categoryId)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public AttributeDefinitionResponse update(
            UUID businessId,
            UUID actorId,
            UUID categoryId,
            UUID definitionId,
            UpdateAttributeDefinitionRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        CategoryAttributeDefinition definition =
                lockDefinition(businessId, categoryId, definitionId);
        String name = CatalogNameNormalizer.displayName(request.name());
        String normalizedName = CatalogNameNormalizer.normalized(name);
        ensureUniqueName(categoryId, normalizedName, definitionId);
        boolean hasValues = valueRepository.existsByDefinition_Id(definitionId);
        if (hasValues) {
            if (request.type() != definition.getType()
                    || request.required() != definition.isRequired()
                    || !constraintsEqual(definition, request.constraints())) {
                throw new BusinessConflictException(
                        "Attribute rules cannot change after product values exist.");
            }
            definition.updateDisplayNameAndDescription(
                    name, normalizedName, normalizeDescription(request.description()));
        } else {
            validateDefinition(
                    request.type(),
                    request.required(),
                    request.constraints(),
                    optionRequestsForExisting(definition));
            definition.update(
                    name,
                    normalizedName,
                    request.type(),
                    request.required(),
                    normalizeDescription(request.description()),
                    request.constraints().minTextLength(),
                    request.constraints().maxTextLength(),
                    request.constraints().minInteger(),
                    request.constraints().maxInteger(),
                    request.constraints().minDecimal(),
                    request.constraints().maxDecimal(),
                    request.constraints().minDate(),
                    request.constraints().maxDate(),
                    request.constraints().minSelections(),
                    request.constraints().maxSelections());
        }
        definitionRepository.flush();
        syncQueueService.enqueueChange(businessId, "PRODUCT_ATTRIBUTES");
        return response(definition);
    }

    @Transactional
    public AttributeDefinitionResponse addOption(
            UUID businessId,
            UUID actorId,
            UUID categoryId,
            UUID definitionId,
            AttributeOptionRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        CategoryAttributeDefinition definition =
                lockDefinition(businessId, categoryId, definitionId);
        requireDropdown(definition);
        requireOptionsMutable(definitionId);
        String label = CatalogNameNormalizer.displayName(request.label());
        String normalizedLabel = CatalogNameNormalizer.normalized(label);
        if (optionRepository.existsByDefinition_IdAndNormalizedLabel(
                definitionId, normalizedLabel)) {
            throw new BusinessConflictException(
                    "An option with that label already exists.");
        }
        if (request.position() < 0) {
            throw new CatalogValidationException("position", "Position cannot be negative.");
        }
        optionRepository.saveAndFlush(CategoryAttributeOption.create(
                definition,
                label,
                normalizedLabel,
                request.active(),
                request.position()));
        syncQueueService.enqueueChange(businessId, "PRODUCT_ATTRIBUTES");
        return response(definition);
    }

    @Transactional
    public AttributeDefinitionResponse updateOption(
            UUID businessId,
            UUID actorId,
            UUID categoryId,
            UUID definitionId,
            UUID optionId,
            AttributeOptionRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        CategoryAttributeDefinition definition =
                lockDefinition(businessId, categoryId, definitionId);
        requireDropdown(definition);
        requireOptionsMutable(definitionId);
        CategoryAttributeOption option = optionRepository
                .findByIdAndDefinition_Id(optionId, definitionId)
                .orElseThrow(() -> new BusinessConflictException("Attribute option not found."));
        String label = CatalogNameNormalizer.displayName(request.label());
        String normalizedLabel = CatalogNameNormalizer.normalized(label);
        boolean duplicate = optionRepository.existsByDefinition_IdAndNormalizedLabel(
                definitionId, normalizedLabel);
        if (duplicate && !option.getNormalizedLabel().equals(normalizedLabel)) {
            throw new BusinessConflictException(
                    "An option with that label already exists.");
        }
        if (request.position() < 0) {
            throw new CatalogValidationException("position", "Position cannot be negative.");
        }
        option.update(label, normalizedLabel, request.active(), request.position());
        optionRepository.flush();
        syncQueueService.enqueueChange(businessId, "PRODUCT_ATTRIBUTES");
        return response(definition);
    }

    @Transactional
    public void deleteOption(
            UUID businessId,
            UUID actorId,
            UUID categoryId,
            UUID definitionId,
            UUID optionId) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        CategoryAttributeDefinition definition =
                lockDefinition(businessId, categoryId, definitionId);
        requireDropdown(definition);
        requireOptionsMutable(definitionId);
        CategoryAttributeOption option = optionRepository
                .findByIdAndDefinition_Id(optionId, definitionId)
                .orElseThrow(() -> new BusinessConflictException("Attribute option not found."));
        if (selectionRepository.existsByOption_Id(optionId)) {
            throw new BusinessConflictException(
                    "An option used by a product cannot be deleted.");
        }
        optionRepository.delete(option);
        syncQueueService.enqueueChange(businessId, "PRODUCT_ATTRIBUTES");
    }

    @Transactional
    public void archiveOrDelete(
            UUID businessId, UUID actorId, UUID categoryId, UUID definitionId) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        CategoryAttributeDefinition definition =
                lockDefinition(businessId, categoryId, definitionId);
        if (valueRepository.existsByDefinition_Id(definitionId)) {
            definition.archive(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            syncQueueService.enqueueChange(businessId, "PRODUCT_ATTRIBUTES");
            return;
        }
        optionRepository.deleteAllInBatch(
                optionRepository.findAllByDefinition_IdOrderByPositionAscLabelAsc(definitionId));
        definitionRepository.delete(definition);
        syncQueueService.enqueueChange(businessId, "PRODUCT_ATTRIBUTES");
    }

    private CategoryAttributeDefinition lockDefinition(
            UUID businessId, UUID categoryId, UUID definitionId) {
        return definitionRepository.findForUpdate(definitionId, businessId, categoryId)
                .filter(CategoryAttributeDefinition::isActive)
                .orElseThrow(com.stockwise.business.application.BusinessNotFoundException::new);
    }

    private void ensureUniqueName(UUID categoryId, String normalizedName, UUID exceptId) {
        if (!definitionRepository.existsByCategory_IdAndNormalizedNameAndArchivedAtIsNull(
                categoryId, normalizedName)) {
            return;
        }
        if (exceptId == null || definitionRepository
                .findById(exceptId)
                .map(definition -> !definition.getNormalizedName().equals(normalizedName))
                .orElse(true)) {
            throw new BusinessConflictException(
                    "An active attribute with that name already exists in this category.");
        }
    }

    private void validateDefinition(
            AttributeType type,
            boolean required,
            AttributeConstraintsRequest constraints,
            List<AttributeOptionRequest> options) {
        Objects.requireNonNull(type);
        validateConstraints(type, constraints);
        boolean dropdown = type == AttributeType.SINGLE_SELECT
                || type == AttributeType.MULTI_SELECT;
        if (!dropdown && !options.isEmpty()) {
            throw new CatalogValidationException(
                    "options", "Options are only valid for dropdown attributes.");
        }
        if (dropdown && options.stream().noneMatch(AttributeOptionRequest::active)) {
            throw new CatalogValidationException(
                    "options", "Dropdown attributes require at least one active option.");
        }
        if (type == AttributeType.MULTI_SELECT
                && required
                && constraints.maxSelections() != null
                && constraints.maxSelections() < 1) {
            invalidConstraint("constraints", "Required multi-select attributes need a selection.");
        }
        if (type == AttributeType.MULTI_SELECT
                && constraints.minSelections() != null
                && constraints.minSelections()
                        > options.stream().filter(AttributeOptionRequest::active).count()) {
            invalidConstraint("constraints", "Minimum selections exceed the active options.");
        }
        ensureUniqueOptionLabels(options);
    }

    private void validateConstraints(
            AttributeType type, AttributeConstraintsRequest constraints) {
        if (type != AttributeType.TEXT
                && (constraints.minTextLength() != null || constraints.maxTextLength() != null)) {
            invalidConstraint("constraints", "Text length constraints require TEXT type.");
        }
        if (type == AttributeType.TEXT) {
            Integer min = constraints.minTextLength();
            Integer max = constraints.maxTextLength();
            if ((min != null && (min < 0 || min > 1000))
                    || (max != null && (max < 0 || max > 1000))
                    || (min != null && max != null && min > max)) {
                invalidConstraint("constraints", "Text length constraints are invalid.");
            }
        }
        if (type != AttributeType.INTEGER
                && (constraints.minInteger() != null || constraints.maxInteger() != null)) {
            invalidConstraint("constraints", "Integer bounds require INTEGER type.");
        }
        if (type == AttributeType.INTEGER
                && greaterThan(constraints.minInteger(), constraints.maxInteger())) {
            invalidConstraint("constraints", "Integer minimum exceeds maximum.");
        }
        validateIntegerBound(constraints.minInteger());
        validateIntegerBound(constraints.maxInteger());
        if (type != AttributeType.DECIMAL
                && (constraints.minDecimal() != null || constraints.maxDecimal() != null)) {
            invalidConstraint("constraints", "Decimal bounds require DECIMAL type.");
        }
        if (type == AttributeType.DECIMAL) {
            validateDecimalBound(constraints.minDecimal());
            validateDecimalBound(constraints.maxDecimal());
            if (greaterThan(constraints.minDecimal(), constraints.maxDecimal())) {
                invalidConstraint("constraints", "Decimal minimum exceeds maximum.");
            }
        }
        if (type != AttributeType.DATE
                && (constraints.minDate() != null || constraints.maxDate() != null)) {
            invalidConstraint("constraints", "Date bounds require DATE type.");
        }
        if (type == AttributeType.DATE
                && constraints.minDate() != null
                && constraints.maxDate() != null
                && constraints.minDate().isAfter(constraints.maxDate())) {
            invalidConstraint("constraints", "Date minimum exceeds maximum.");
        }
        if (type != AttributeType.MULTI_SELECT
                && (constraints.minSelections() != null
                || constraints.maxSelections() != null)) {
            invalidConstraint("constraints", "Selection counts require MULTI_SELECT type.");
        }
        if (type == AttributeType.MULTI_SELECT) {
            Integer min = constraints.minSelections();
            Integer max = constraints.maxSelections();
            if ((min != null && min < 0)
                    || (max != null && max < 0)
                    || (min != null && max != null && min > max)) {
                invalidConstraint("constraints", "Selection count constraints are invalid.");
            }
        }
    }

    private void validateDecimalBound(BigDecimal bound) {
        BigDecimal normalized = bound == null ? null : bound.stripTrailingZeros();
        if (normalized != null
                && (precision(normalized) > 18 || Math.max(normalized.scale(), 0) > 6)) {
            invalidConstraint("constraints", "Decimal bounds support at most 18 digits and scale 6.");
        }
    }

    private void validateIntegerBound(BigInteger bound) {
        if (bound != null && bound.abs().toString().length() > 65) {
            invalidConstraint("constraints", "Integer bounds exceed the supported database range.");
        }
    }

    private boolean constraintsEqual(
            CategoryAttributeDefinition definition,
            AttributeConstraintsRequest constraints) {
        return Objects.equals(definition.getMinTextLength(), constraints.minTextLength())
                && Objects.equals(definition.getMaxTextLength(), constraints.maxTextLength())
                && Objects.equals(definition.getMinInteger(), constraints.minInteger())
                && Objects.equals(definition.getMaxInteger(), constraints.maxInteger())
                && decimalEquals(definition.getMinDecimal(), constraints.minDecimal())
                && decimalEquals(definition.getMaxDecimal(), constraints.maxDecimal())
                && Objects.equals(definition.getMinDate(), constraints.minDate())
                && Objects.equals(definition.getMaxDate(), constraints.maxDate())
                && Objects.equals(definition.getMinSelections(), constraints.minSelections())
                && Objects.equals(definition.getMaxSelections(), constraints.maxSelections());
    }

    private boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private boolean greaterThan(BigInteger min, BigInteger max) {
        return min != null && max != null && min.compareTo(max) > 0;
    }

    private boolean greaterThan(BigDecimal min, BigDecimal max) {
        return min != null && max != null && min.compareTo(max) > 0;
    }

    private int precision(BigDecimal value) {
        return value.precision() + Math.max(0, -value.scale());
    }

    private void ensureUniqueOptionLabels(List<AttributeOptionRequest> options) {
        List<String> normalized = new ArrayList<>();
        for (AttributeOptionRequest option : options) {
            String name = CatalogNameNormalizer.normalized(option.label());
            if (normalized.contains(name)) {
                throw new CatalogValidationException(
                        "options", "Dropdown option labels must be unique.");
            }
            normalized.add(name);
            if (option.position() < 0) {
                throw new CatalogValidationException(
                        "options", "Dropdown option positions cannot be negative.");
            }
        }
    }

    private List<AttributeOptionRequest> optionRequestsForExisting(
            CategoryAttributeDefinition definition) {
        return optionRepository.findAllByDefinition_IdOrderByPositionAscLabelAsc(definition.getId())
                .stream()
                .map(option -> new AttributeOptionRequest(
                        option.getLabel(), option.isActive(), option.getPosition()))
                .toList();
    }

    private void saveOptions(
            CategoryAttributeDefinition definition, List<AttributeOptionRequest> options) {
        List<CategoryAttributeOption> entities = options.stream()
                .map(option -> {
                    String label = CatalogNameNormalizer.displayName(option.label());
                    return CategoryAttributeOption.create(
                            definition,
                            label,
                            CatalogNameNormalizer.normalized(label),
                            option.active(),
                            option.position());
                })
                .toList();
        optionRepository.saveAll(entities);
        optionRepository.flush();
    }

    private void requireDropdown(CategoryAttributeDefinition definition) {
        if (definition.getType() != AttributeType.SINGLE_SELECT
                && definition.getType() != AttributeType.MULTI_SELECT) {
            throw new BusinessConflictException(
                    "Only dropdown attributes can have options.");
        }
    }

    private void requireOptionsMutable(UUID definitionId) {
        if (valueRepository.existsByDefinition_Id(definitionId)) {
            throw new BusinessConflictException(
                    "Dropdown options cannot change after product values exist.");
        }
    }

    private void invalidConstraint(String field, String message) {
        throw new CatalogValidationException(field, message);
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private AttributeDefinitionResponse response(CategoryAttributeDefinition definition) {
        List<AttributeOptionResponse> options = optionRepository
                .findAllByDefinition_IdOrderByPositionAscLabelAsc(definition.getId())
                .stream()
                .map(AttributeOptionResponse::from)
                .toList();
        return AttributeDefinitionResponse.from(
                definition,
                AttributeDefinitionResponse.AttributeConstraintsResponse.from(definition),
                options);
    }
}
