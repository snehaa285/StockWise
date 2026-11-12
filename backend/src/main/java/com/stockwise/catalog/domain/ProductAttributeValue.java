package com.stockwise.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_attribute_value")
public class ProductAttributeValue {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "business_id", nullable = false, columnDefinition = "BINARY(16)")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID businessId;

    @Column(name = "category_id", nullable = false, columnDefinition = "BINARY(16)")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private CategoryAttributeDefinition definition;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private AttributeType valueType;

    @Column(name = "text_value", length = 1000)
    private String textValue;

    @Column(name = "integer_value", precision = 65, scale = 0)
    private BigInteger integerValue;

    @Column(name = "decimal_value", precision = 24, scale = 6)
    private BigDecimal decimalValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "date_value")
    private LocalDate dateValue;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected ProductAttributeValue() {
    }

    private ProductAttributeValue(
            UUID id,
            Product product,
            CategoryAttributeDefinition definition,
            AttributeType valueType,
            String textValue,
            BigInteger integerValue,
            BigDecimal decimalValue,
            Boolean booleanValue,
            LocalDate dateValue) {
        this.id = id;
        this.businessId = product.getBusiness().getId();
        this.categoryId = product.getCategory().getId();
        this.product = product;
        this.definition = definition;
        this.valueType = valueType;
        this.textValue = textValue;
        this.integerValue = integerValue;
        this.decimalValue = decimalValue;
        this.booleanValue = booleanValue;
        this.dateValue = dateValue;
    }

    public static ProductAttributeValue create(
            Product product,
            CategoryAttributeDefinition definition,
            AttributeType valueType,
            String textValue,
            BigInteger integerValue,
            BigDecimal decimalValue,
            Boolean booleanValue,
            LocalDate dateValue) {
        return new ProductAttributeValue(
                UUID.randomUUID(),
                product,
                definition,
                valueType,
                textValue,
                integerValue,
                decimalValue,
                booleanValue,
                dateValue);
    }

    @PrePersist
    void setCreationTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public CategoryAttributeDefinition getDefinition() {
        return definition;
    }

    public AttributeType getValueType() {
        return valueType;
    }

    public String getTextValue() {
        return textValue;
    }

    public BigInteger getIntegerValue() {
        return integerValue;
    }

    public BigDecimal getDecimalValue() {
        return decimalValue;
    }

    public Boolean getBooleanValue() {
        return booleanValue;
    }

    public LocalDate getDateValue() {
        return dateValue;
    }
}
