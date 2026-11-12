package com.stockwise.inventory.domain;

import com.stockwise.auth.domain.AppUser;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inventory_movement")
public class InventoryMovement {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private InventoryOperation operation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    @Column(name = "movement_type", nullable = false, length = 24)
    private String movementType;

    @Column(name = "quantity_delta", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityDelta;

    @Column(name = "quantity_after", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id")
    private InventoryLocation sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private InventoryLocation destinationLocation;

    @Column(name = "reason", length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private AppUser actor;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected InventoryMovement() {
    }

    private InventoryMovement(
            UUID id,
            Business business,
            InventoryOperation operation,
            Product product,
            InventoryLocation location,
            InventoryBatch batch,
            String movementType,
            BigDecimal quantityDelta,
            BigDecimal quantityAfter,
            InventoryLocation sourceLocation,
            InventoryLocation destinationLocation,
            String reason,
            AppUser actor) {
        this.id = id;
        this.business = business;
        this.operation = operation;
        this.product = product;
        this.location = location;
        this.batch = batch;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.quantityAfter = quantityAfter;
        this.sourceLocation = sourceLocation;
        this.destinationLocation = destinationLocation;
        this.reason = reason;
        this.actor = actor;
    }

    public static InventoryMovement create(
            Business business,
            InventoryOperation operation,
            Product product,
            InventoryLocation location,
            InventoryBatch batch,
            String movementType,
            BigDecimal quantityDelta,
            BigDecimal quantityAfter,
            InventoryLocation sourceLocation,
            InventoryLocation destinationLocation,
            String reason,
            AppUser actor) {
        return new InventoryMovement(
                UUID.randomUUID(),
                business,
                operation,
                product,
                location,
                batch,
                movementType,
                quantityDelta,
                quantityAfter,
                sourceLocation,
                destinationLocation,
                reason,
                actor);
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

    public InventoryOperation getOperation() {
        return operation;
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

    public String getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public InventoryLocation getSourceLocation() {
        return sourceLocation;
    }

    public InventoryLocation getDestinationLocation() {
        return destinationLocation;
    }

    public String getReason() {
        return reason;
    }

    public AppUser getActor() {
        return actor;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
