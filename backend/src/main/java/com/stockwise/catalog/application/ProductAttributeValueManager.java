package com.stockwise.catalog.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockwise.catalog.domain.AttributeType;
import com.stockwise.catalog.domain.CategoryAttributeDefinition;
import com.stockwise.catalog.domain.CategoryAttributeOption;
import com.stockwise.catalog.domain.Product;
import com.stockwise.catalog.domain.ProductAttributeSelection;
import com.stockwise.catalog.domain.ProductAttributeValue;
import com.stockwise.catalog.persistence.CategoryAttributeDefinitionRepository;
import com.stockwise.catalog.persistence.CategoryAttributeOptionRepository;
import com.stockwise.catalog.persistence.ProductAttributeSelectionRepository;
import com.stockwise.catalog.persistence.ProductAttributeValueRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

@Service
@Profile("mysql")
public class ProductAttributeValueManager {
    private final CategoryAttributeDefinitionRepository definitionRepository;
    private final CategoryAttributeOptionRepository optionRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductAttributeSelectionRepository selectionRepository;

    public ProductAttributeValueManager(
            CategoryAttributeDefinitionRepository definitionRepository,
            CategoryAttributeOptionRepository optionRepository,
            ProductAttributeValueRepository valueRepository,
            ProductAttributeSelectionRepository selectionRepository) {
        this.definitionRepository = definitionRepository;
        this.optionRepository = optionRepository;
        this.valueRepository = valueRepository;
        this.selectionRepository = selectionRepository;
    }

    public void replaceActiveValues(Product product, Map<UUID, JsonNode> requestedValues) {
        List<CategoryAttributeDefinition> definitions = definitionRepository
                .findAllByBusiness_IdAndCategory_IdAndArchivedAtIsNullOrderByNameAsc(
                        product.getBusiness().getId(), product.getCategory().getId())
                .stream()
                .sorted(Comparator.comparing(CategoryAttributeDefinition::getId))
                .map(definition -> definitionRepository.findForUpdate(
                        definition.getId(),
                        product.getBusiness().getId(),
                        product.getCategory().getId())
                        .orElseThrow(() -> new CatalogValidationException(
                                "attributes", "An attribute definition is no longer available.")))
                .toList();
        Map<UUID, CategoryAttributeDefinition> definitionsById = new HashMap<>();
        definitions.forEach(definition -> definitionsById.put(definition.getId(), definition));

        for (UUID requestedId : requestedValues.keySet()) {
            if (!definitionsById.containsKey(requestedId)) {
                throw new CatalogValidationException(
                        "attributes", "An attribute does not belong to this product category.");
            }
        }

        Map<UUID, DecodedValue> decodedValues = new LinkedHashMap<>();
        for (CategoryAttributeDefinition definition : definitions) {
            JsonNode requested = requestedValues.get(definition.getId());
            DecodedValue decoded = decode(definition, requested);
            if (decoded == null && definition.isRequired()) {
                throw new CatalogValidationException(
                        "attributes." + definition.getId(),
                        "A valid value is required for this attribute.");
            }
            if (decoded != null) {
                decodedValues.put(definition.getId(), decoded);
            }
        }

        List<ProductAttributeValue> existingValues =
                valueRepository.findAllByProduct_IdAndDefinition_ArchivedAtIsNull(
                        product.getId());
        existingValues.forEach(value -> {
            selectionRepository.deleteAllByValue_Id(value.getId());
            valueRepository.delete(value);
        });
        valueRepository.flush();

        List<ProductAttributeValue> savedValues = new ArrayList<>();
        Map<UUID, DecodedValue> savedSelections = new LinkedHashMap<>();
        decodedValues.forEach((definitionId, decoded) -> {
            CategoryAttributeDefinition definition = definitionsById.get(definitionId);
            ProductAttributeValue value = valueRepository.save(
                    ProductAttributeValue.create(
                            product,
                            definition,
                            definition.getType(),
                            decoded.textValue(),
                            decoded.integerValue(),
                            decoded.decimalValue(),
                            decoded.booleanValue(),
                            decoded.dateValue()));
            savedValues.add(value);
            if (!decoded.optionIds().isEmpty()) {
                savedSelections.put(value.getId(), decoded);
            }
        });
        valueRepository.flush();

        Map<UUID, ProductAttributeValue> valuesById = new HashMap<>();
        savedValues.forEach(value -> valuesById.put(value.getId(), value));
        List<ProductAttributeSelection> selections = new ArrayList<>();
        savedSelections.forEach((valueId, decoded) -> {
            ProductAttributeValue value = valuesById.get(valueId);
            Map<UUID, CategoryAttributeOption> options =
                    activeOptionsById(value.getDefinition());
            decoded.optionIds().forEach(optionId ->
                    selections.add(ProductAttributeSelection.create(value, options.get(optionId))));
        });
        selectionRepository.saveAll(selections);
    }

    public Map<UUID, Object> valuesForProduct(UUID productId) {
        Map<UUID, Object> values = new LinkedHashMap<>();
        for (ProductAttributeValue value : valueRepository.findAllByProduct_Id(productId)) {
            Object stored = switch (value.getValueType()) {
                case TEXT -> value.getTextValue();
                case INTEGER -> value.getIntegerValue();
                case DECIMAL -> value.getDecimalValue().stripTrailingZeros();
                case BOOLEAN -> value.getBooleanValue();
                case DATE -> value.getDateValue().toString();
                case SINGLE_SELECT -> selectionRepository
                        .findAllByValue_IdOrderByOption_PositionAsc(value.getId())
                        .stream()
                        .map(selection -> selection.getOption().getId())
                        .findFirst()
                        .orElse(null);
                case MULTI_SELECT -> selectionRepository
                        .findAllByValue_IdOrderByOption_PositionAsc(value.getId())
                        .stream()
                        .map(selection -> selection.getOption().getId())
                        .toList();
            };
            values.put(value.getDefinition().getId(), stored);
        }
        return values;
    }

    private DecodedValue decode(
            CategoryAttributeDefinition definition, JsonNode input) {
        if (input == null || input.isNull()) {
            return null;
        }
        return switch (definition.getType()) {
            case TEXT -> decodeText(definition, input);
            case INTEGER -> decodeInteger(definition, input);
            case DECIMAL -> decodeDecimal(definition, input);
            case BOOLEAN -> decodeBoolean(input);
            case DATE -> decodeDate(definition, input);
            case SINGLE_SELECT -> decodeSingleSelect(definition, input);
            case MULTI_SELECT -> decodeMultiSelect(definition, input);
        };
    }

    private DecodedValue decodeText(CategoryAttributeDefinition definition, JsonNode input) {
        if (!input.isTextual()) {
            invalid(definition, "Enter text for this attribute.");
        }
        String text = input.textValue().trim();
        int length = text.codePointCount(0, text.length());
        int max = definition.getMaxTextLength() == null
                ? 1000 : Math.min(definition.getMaxTextLength(), 1000);
        int min = definition.getMinTextLength() == null ? 0 : definition.getMinTextLength();
        if (text.isEmpty()) {
            return null;
        }
        if (length < min || length > max) {
            invalid(definition, "Text length is outside the allowed range.");
        }
        return DecodedValue.text(text);
    }

    private DecodedValue decodeInteger(CategoryAttributeDefinition definition, JsonNode input) {
        if (!input.isIntegralNumber()) {
            invalid(definition, "Enter a whole number for this attribute.");
        }
        BigInteger number = input.bigIntegerValue();
        if (number.abs().toString().length() > 65) {
            invalid(definition, "The whole number exceeds the supported database range.");
        }
        if ((definition.getMinInteger() != null
                && number.compareTo(definition.getMinInteger()) < 0)
                || (definition.getMaxInteger() != null
                && number.compareTo(definition.getMaxInteger()) > 0)) {
            invalid(definition, "Whole number is outside the allowed range.");
        }
        return DecodedValue.integer(number);
    }

    private DecodedValue decodeDecimal(CategoryAttributeDefinition definition, JsonNode input) {
        if (!input.isNumber()) {
            invalid(definition, "Enter a decimal number for this attribute.");
        }
        BigDecimal number = input.decimalValue();
        BigDecimal normalized = number.stripTrailingZeros();
        if (precision(normalized) > 18 || Math.max(normalized.scale(), 0) > 6) {
            invalid(definition, "Decimal values support at most 18 digits and scale 6.");
        }
        if ((definition.getMinDecimal() != null
                && number.compareTo(definition.getMinDecimal()) < 0)
                || (definition.getMaxDecimal() != null
                && number.compareTo(definition.getMaxDecimal()) > 0)) {
            invalid(definition, "Decimal value is outside the allowed range.");
        }
        return DecodedValue.decimal(normalized);
    }

    private DecodedValue decodeBoolean(JsonNode input) {
        if (!input.isBoolean()) {
            throw new CatalogValidationException(
                    "attributes", "Enter true or false for this attribute.");
        }
        return DecodedValue.bool(input.booleanValue());
    }

    private DecodedValue decodeDate(CategoryAttributeDefinition definition, JsonNode input) {
        if (!input.isTextual()) {
            invalid(definition, "Enter a date in ISO YYYY-MM-DD format.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(input.textValue());
        } catch (DateTimeParseException exception) {
            invalid(definition, "Enter a date in ISO YYYY-MM-DD format.");
            return null;
        }
        if ((definition.getMinDate() != null && date.isBefore(definition.getMinDate()))
                || (definition.getMaxDate() != null && date.isAfter(definition.getMaxDate()))) {
            invalid(definition, "Date is outside the allowed range.");
        }
        return DecodedValue.date(date);
    }

    private DecodedValue decodeSingleSelect(
            CategoryAttributeDefinition definition, JsonNode input) {
        if (!input.isTextual()) {
            invalid(definition, "Select one predefined option.");
        }
        UUID optionId = parseOptionId(definition, input.textValue());
        if (!activeOptionsById(definition).containsKey(optionId)) {
            invalid(definition, "Selected option is not available.");
        }
        return DecodedValue.options(List.of(optionId));
    }

    private DecodedValue decodeMultiSelect(
            CategoryAttributeDefinition definition, JsonNode input) {
        if (!input.isArray()) {
            invalid(definition, "Select a list of predefined options.");
        }
        List<UUID> optionIds = new ArrayList<>();
        Set<UUID> uniqueIds = new HashSet<>();
        Map<UUID, CategoryAttributeOption> activeOptions = activeOptionsById(definition);
        for (JsonNode optionNode : input) {
            if (!optionNode.isTextual()) {
                invalid(definition, "Each selected option must be an option ID.");
            }
            UUID optionId = parseOptionId(definition, optionNode.textValue());
            if (!uniqueIds.add(optionId)) {
                invalid(definition, "Duplicate selected options are not allowed.");
            }
            if (!activeOptions.containsKey(optionId)) {
                invalid(definition, "A selected option is not available.");
            }
            optionIds.add(optionId);
        }
        if (optionIds.isEmpty()) {
            return null;
        }
        int min = definition.isRequired()
                ? Math.max(1, definition.getMinSelections() == null
                        ? 0 : definition.getMinSelections())
                : definition.getMinSelections() == null ? 0 : definition.getMinSelections();
        if (optionIds.size() < min
                || (definition.getMaxSelections() != null
                && optionIds.size() > definition.getMaxSelections())) {
            invalid(definition, "Selected option count is outside the allowed range.");
        }
        return DecodedValue.options(optionIds);
    }

    private UUID parseOptionId(CategoryAttributeDefinition definition, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            invalid(definition, "Selected option is not available.");
            return null;
        }
    }

    private Map<UUID, CategoryAttributeOption> activeOptionsById(
            CategoryAttributeDefinition definition) {
        Map<UUID, CategoryAttributeOption> options = new HashMap<>();
        optionRepository
                .findAllByDefinition_IdAndActiveTrueOrderByPositionAscLabelAsc(
                        definition.getId())
                .forEach(option -> options.put(option.getId(), option));
        return options;
    }

    private int precision(BigDecimal value) {
        return value.precision() + Math.max(0, -value.scale());
    }

    private void invalid(CategoryAttributeDefinition definition, String message) {
        throw new CatalogValidationException(
                "attributes." + definition.getId(), message);
    }

    private record DecodedValue(
            String textValue,
            BigInteger integerValue,
            BigDecimal decimalValue,
            Boolean booleanValue,
            LocalDate dateValue,
            List<UUID> optionIds) {
        static DecodedValue text(String value) {
            return new DecodedValue(value, null, null, null, null, List.of());
        }

        static DecodedValue integer(BigInteger value) {
            return new DecodedValue(null, value, null, null, null, List.of());
        }

        static DecodedValue decimal(BigDecimal value) {
            return new DecodedValue(null, null, value, null, null, List.of());
        }

        static DecodedValue bool(Boolean value) {
            return new DecodedValue(null, null, null, value, null, List.of());
        }

        static DecodedValue date(LocalDate value) {
            return new DecodedValue(null, null, null, null, value, List.of());
        }

        static DecodedValue options(List<UUID> value) {
            return new DecodedValue(null, null, null, null, null, value);
        }
    }
}
