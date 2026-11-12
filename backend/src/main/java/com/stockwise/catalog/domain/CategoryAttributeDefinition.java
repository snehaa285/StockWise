package com.stockwise.catalog.domain;

import com.stockwise.business.domain.Business;
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
@Table(name = "category_attribute_definition")
public class CategoryAttributeDefinition {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false, length = 20)
    private AttributeType type;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "min_text_length")
    private Integer minTextLength;

    @Column(name = "max_text_length")
    private Integer maxTextLength;

    @Column(name = "min_integer", precision = 65, scale = 0)
    private BigInteger minInteger;

    @Column(name = "max_integer", precision = 65, scale = 0)
    private BigInteger maxInteger;

    @Column(name = "min_decimal", precision = 24, scale = 6)
    private BigDecimal minDecimal;

    @Column(name = "max_decimal", precision = 24, scale = 6)
    private BigDecimal maxDecimal;

    @Column(name = "min_date")
    private LocalDate minDate;

    @Column(name = "max_date")
    private LocalDate maxDate;

    @Column(name = "min_selections")
    private Integer minSelections;

    @Column(name = "max_selections")
    private Integer maxSelections;

    @Column(name = "archived_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected CategoryAttributeDefinition() {
    }

    private CategoryAttributeDefinition(
            UUID id,
            Business business,
            ProductCategory category,
            String name,
            String normalizedName,
            AttributeType type,
            boolean required,
            String description,
            Integer minTextLength,
            Integer maxTextLength,
            BigInteger minInteger,
            BigInteger maxInteger,
            BigDecimal minDecimal,
            BigDecimal maxDecimal,
            LocalDate minDate,
            LocalDate maxDate,
            Integer minSelections,
            Integer maxSelections) {
        this.id = id;
        this.business = business;
        this.category = category;
        this.name = name;
        this.normalizedName = normalizedName;
        this.type = type;
        this.required = required;
        this.description = description;
        this.minTextLength = minTextLength;
        this.maxTextLength = maxTextLength;
        this.minInteger = minInteger;
        this.maxInteger = maxInteger;
        this.minDecimal = minDecimal;
        this.maxDecimal = maxDecimal;
        this.minDate = minDate;
        this.maxDate = maxDate;
        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
    }

    public static CategoryAttributeDefinition create(
            Business business,
            ProductCategory category,
            String name,
            String normalizedName,
            AttributeType type,
            boolean required,
            String description,
            Integer minTextLength,
            Integer maxTextLength,
            BigInteger minInteger,
            BigInteger maxInteger,
            BigDecimal minDecimal,
            BigDecimal maxDecimal,
            LocalDate minDate,
            LocalDate maxDate,
            Integer minSelections,
            Integer maxSelections) {
        return new CategoryAttributeDefinition(
                UUID.randomUUID(),
                business,
                category,
                name,
                normalizedName,
                type,
                required,
                description,
                minTextLength,
                maxTextLength,
                minInteger,
                maxInteger,
                minDecimal,
                maxDecimal,
                minDate,
                maxDate,
                minSelections,
                maxSelections);
    }

    public void update(
            String name,
            String normalizedName,
            AttributeType type,
            boolean required,
            String description,
            Integer minTextLength,
            Integer maxTextLength,
            BigInteger minInteger,
            BigInteger maxInteger,
            BigDecimal minDecimal,
            BigDecimal maxDecimal,
            LocalDate minDate,
            LocalDate maxDate,
            Integer minSelections,
            Integer maxSelections) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.type = type;
        this.required = required;
        this.description = description;
        this.minTextLength = minTextLength;
        this.maxTextLength = maxTextLength;
        this.minInteger = minInteger;
        this.maxInteger = maxInteger;
        this.minDecimal = minDecimal;
        this.maxDecimal = maxDecimal;
        this.minDate = minDate;
        this.maxDate = maxDate;
        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
    }

    public void updateDisplayNameAndDescription(
            String name, String normalizedName, String description) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.description = description;
    }

    public void archive(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
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

    public Business getBusiness() {
        return business;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public AttributeType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public String getDescription() {
        return description;
    }

    public Integer getMinTextLength() {
        return minTextLength;
    }

    public Integer getMaxTextLength() {
        return maxTextLength;
    }

    public BigInteger getMinInteger() {
        return minInteger;
    }

    public BigInteger getMaxInteger() {
        return maxInteger;
    }

    public BigDecimal getMinDecimal() {
        return minDecimal;
    }

    public BigDecimal getMaxDecimal() {
        return maxDecimal;
    }

    public LocalDate getMinDate() {
        return minDate;
    }

    public LocalDate getMaxDate() {
        return maxDate;
    }

    public Integer getMinSelections() {
        return minSelections;
    }

    public Integer getMaxSelections() {
        return maxSelections;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public boolean isActive() {
        return archivedAt == null;
    }
}
