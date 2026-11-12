package com.stockwise.inventory.domain;

import com.stockwise.business.domain.Business;
import com.stockwise.auth.domain.AppUser;
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
@Table(name = "inventory_operation")
public class InventoryOperation {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "operation_type", nullable = false, length = 24)
    private String operationType;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "request_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdBy;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected InventoryOperation() {
    }

    private InventoryOperation(
            UUID id,
            Business business,
            String operationType,
            String idempotencyKey,
            byte[] requestHash,
            AppUser createdBy) {
        this.id = id;
        this.business = business;
        this.operationType = operationType;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash.clone();
        this.createdBy = createdBy;
    }

    public static InventoryOperation create(
            Business business,
            String operationType,
            String idempotencyKey,
            byte[] requestHash,
            AppUser createdBy) {
        return new InventoryOperation(
                UUID.randomUUID(), business, operationType, idempotencyKey, requestHash, createdBy);
    }

    public void complete(String resultJson) {
        this.resultJson = resultJson;
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

    public String getOperationType() {
        return operationType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public byte[] getRequestHash() {
        return requestHash.clone();
    }

    public String getResultJson() {
        return resultJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
