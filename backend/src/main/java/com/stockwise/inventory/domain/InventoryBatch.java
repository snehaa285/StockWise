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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inventory_batch")
public class InventoryBatch {
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

    @Column(name = "batch_code", nullable = false, length = 120)
    private String batchCode;

    @Column(name = "normalized_batch_code", nullable = false, length = 120)
    private String normalizedBatchCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected InventoryBatch() {
    }

    private InventoryBatch(
            UUID id,
            Business business,
            Product product,
            String batchCode,
            String normalizedBatchCode,
            LocalDate expiryDate) {
        this.id = id;
        this.business = business;
        this.product = product;
        this.batchCode = batchCode;
        this.normalizedBatchCode = normalizedBatchCode;
        this.expiryDate = expiryDate;
    }

    public static InventoryBatch create(
            Business business,
            Product product,
            String batchCode,
            String normalizedBatchCode,
            LocalDate expiryDate) {
        return new InventoryBatch(
                UUID.randomUUID(), business, product, batchCode, normalizedBatchCode, expiryDate);
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
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

    public String getBatchCode() {
        return batchCode;
    }

    public String getNormalizedBatchCode() {
        return normalizedBatchCode;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
