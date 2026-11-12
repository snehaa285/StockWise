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
@Table(name = "product_category")
public class ProductCategory {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "batch_tracking_default", nullable = false)
    private boolean batchTrackingDefault;

    @Column(name = "expiry_tracking_default", nullable = false)
    private boolean expiryTrackingDefault;

    @Column(name = "archived_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected ProductCategory() {
    }

    private ProductCategory(
            UUID id,
            Business business,
            String name,
            String normalizedName,
            String description,
            boolean batchTrackingDefault,
            boolean expiryTrackingDefault) {
        this.id = id;
        this.business = business;
        this.name = name;
        this.normalizedName = normalizedName;
        this.description = description;
        this.batchTrackingDefault = batchTrackingDefault;
        this.expiryTrackingDefault = expiryTrackingDefault;
    }

    public static ProductCategory create(
            Business business,
            String name,
            String normalizedName,
            String description,
            boolean batchTrackingDefault,
            boolean expiryTrackingDefault) {
        return new ProductCategory(
                UUID.randomUUID(),
                business,
                name,
                normalizedName,
                description,
                batchTrackingDefault,
                expiryTrackingDefault);
    }

    public void update(
            String name,
            String normalizedName,
            String description,
            boolean batchTrackingDefault,
            boolean expiryTrackingDefault) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.description = description;
        this.batchTrackingDefault = batchTrackingDefault;
        this.expiryTrackingDefault = expiryTrackingDefault;
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

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isBatchTrackingDefault() {
        return batchTrackingDefault;
    }

    public boolean isExpiryTrackingDefault() {
        return expiryTrackingDefault;
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
