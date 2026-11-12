package com.stockwise.catalog.domain;

import com.stockwise.business.domain.Business;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product")
public class Product {
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

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 160)
    private String normalizedName;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "normalized_sku", length = 100)
    private String normalizedSku;

    @Column(name = "unit_of_measure", nullable = false, length = 40)
    private String unitOfMeasure;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "batch_tracking_override")
    private Boolean batchTrackingOverride;

    @Column(name = "expiry_tracking_override")
    private Boolean expiryTrackingOverride;

    @Column(name = "archived_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Product() {
    }

    private Product(
            UUID id,
            Business business,
            ProductCategory category,
            String name,
            String normalizedName,
            String sku,
            String normalizedSku,
            String unitOfMeasure,
            String description,
            Boolean batchTrackingOverride,
            Boolean expiryTrackingOverride) {
        this.id = id;
        this.business = business;
        this.category = category;
        this.name = name;
        this.normalizedName = normalizedName;
        this.sku = sku;
        this.normalizedSku = normalizedSku;
        this.unitOfMeasure = unitOfMeasure;
        this.description = description;
        this.batchTrackingOverride = batchTrackingOverride;
        this.expiryTrackingOverride = expiryTrackingOverride;
    }

    public static Product create(
            Business business,
            ProductCategory category,
            String name,
            String normalizedName,
            String sku,
            String normalizedSku,
            String unitOfMeasure,
            String description,
            Boolean batchTrackingOverride,
            Boolean expiryTrackingOverride) {
        return new Product(
                UUID.randomUUID(),
                business,
                category,
                name,
                normalizedName,
                sku,
                normalizedSku,
                unitOfMeasure,
                description,
                batchTrackingOverride,
                expiryTrackingOverride);
    }

    public void update(
            ProductCategory category,
            String name,
            String normalizedName,
            String sku,
            String normalizedSku,
            String unitOfMeasure,
            String description,
            Boolean batchTrackingOverride,
            Boolean expiryTrackingOverride) {
        this.category = category;
        this.name = name;
        this.normalizedName = normalizedName;
        this.sku = sku;
        this.normalizedSku = normalizedSku;
        this.unitOfMeasure = unitOfMeasure;
        this.description = description;
        this.batchTrackingOverride = batchTrackingOverride;
        this.expiryTrackingOverride = expiryTrackingOverride;
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

    public String getSku() {
        return sku;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getBatchTrackingOverride() {
        return batchTrackingOverride;
    }

    public Boolean getExpiryTrackingOverride() {
        return expiryTrackingOverride;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return archivedAt == null;
    }
}
