package com.stockwise.inventory.domain;

import com.stockwise.business.domain.Business;
import com.stockwise.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inventory_reorder_level")
public class InventoryReorderLevel {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @Column(name = "reorder_level", nullable = false, precision = 18, scale = 3)
    private BigDecimal reorderLevel;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected InventoryReorderLevel() {
    }

    private InventoryReorderLevel(
            UUID id,
            Business business,
            Product product,
            InventoryLocation location,
            BigDecimal reorderLevel) {
        this.id = id;
        this.business = business;
        this.product = product;
        this.location = location;
        this.reorderLevel = reorderLevel;
    }

    public static InventoryReorderLevel create(
            Business business,
            Product product,
            InventoryLocation location,
            BigDecimal reorderLevel) {
        return new InventoryReorderLevel(
                UUID.randomUUID(), business, product, location, reorderLevel);
    }

    public void update(BigDecimal reorderLevel) {
        this.reorderLevel = reorderLevel;
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

    public Product getProduct() {
        return product;
    }

    public InventoryLocation getLocation() {
        return location;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
