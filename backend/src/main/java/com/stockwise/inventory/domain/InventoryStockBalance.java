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
@Table(name = "inventory_stock_balance")
public class InventoryStockBalance {
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected InventoryStockBalance() {
    }

    private InventoryStockBalance(
            UUID id,
            Business business,
            Product product,
            InventoryLocation location,
            InventoryBatch batch,
            BigDecimal quantity) {
        this.id = id;
        this.business = business;
        this.product = product;
        this.location = location;
        this.batch = batch;
        this.quantity = quantity;
    }

    public static InventoryStockBalance create(
            Business business,
            Product product,
            InventoryLocation location,
            InventoryBatch batch) {
        return new InventoryStockBalance(
                UUID.randomUUID(), business, product, location, batch, BigDecimal.ZERO);
    }

    public BigDecimal add(BigDecimal delta) {
        BigDecimal updated = quantity.add(delta);
        if (updated.signum() < 0) {
            throw new IllegalArgumentException("Stock balance cannot become negative.");
        }
        quantity = updated;
        return quantity;
    }

    @PrePersist
    void setCreatedAt() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
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

    public InventoryBatch getBatch() {
        return batch;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
