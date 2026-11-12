package com.stockwise.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_attribute_selection")
public class ProductAttributeSelection {
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

    @Column(name = "definition_id", nullable = false, columnDefinition = "BINARY(16)")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID definitionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "value_id", nullable = false)
    private ProductAttributeValue value;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private CategoryAttributeOption option;

    protected ProductAttributeSelection() {
    }

    private ProductAttributeSelection(ProductAttributeValue value, CategoryAttributeOption option) {
        this.id = UUID.randomUUID();
        this.businessId = value.getProduct().getBusiness().getId();
        this.categoryId = value.getProduct().getCategory().getId();
        this.definitionId = value.getDefinition().getId();
        this.value = value;
        this.option = option;
    }

    public static ProductAttributeSelection create(
            ProductAttributeValue value, CategoryAttributeOption option) {
        return new ProductAttributeSelection(value, option);
    }

    public UUID getId() {
        return id;
    }

    public ProductAttributeValue getValue() {
        return value;
    }

    public CategoryAttributeOption getOption() {
        return option;
    }
}
