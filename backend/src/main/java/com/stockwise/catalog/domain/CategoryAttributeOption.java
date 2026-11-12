package com.stockwise.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "category_attribute_option")
public class CategoryAttributeOption {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private CategoryAttributeDefinition definition;

    @Column(name = "business_id", nullable = false, columnDefinition = "BINARY(16)")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID businessId;

    @Column(name = "category_id", nullable = false, columnDefinition = "BINARY(16)")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID categoryId;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "normalized_label", nullable = false, length = 120)
    private String normalizedLabel;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected CategoryAttributeOption() {
    }

    private CategoryAttributeOption(
            UUID id,
            CategoryAttributeDefinition definition,
            String label,
            String normalizedLabel,
            boolean active,
            int position) {
        this.id = id;
        this.definition = definition;
        this.businessId = definition.getBusiness().getId();
        this.categoryId = definition.getCategory().getId();
        this.label = label;
        this.normalizedLabel = normalizedLabel;
        this.active = active;
        this.position = position;
    }

    public static CategoryAttributeOption create(
            CategoryAttributeDefinition definition,
            String label,
            String normalizedLabel,
            boolean active,
            int position) {
        return new CategoryAttributeOption(
                UUID.randomUUID(), definition, label, normalizedLabel, active, position);
    }

    public void update(String label, String normalizedLabel, boolean active, int position) {
        this.label = label;
        this.normalizedLabel = normalizedLabel;
        this.active = active;
        this.position = position;
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public CategoryAttributeDefinition getDefinition() {
        return definition;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getLabel() {
        return label;
    }

    public String getNormalizedLabel() {
        return normalizedLabel;
    }

    public boolean isActive() {
        return active;
    }

    public int getPosition() {
        return position;
    }
}
