package com.stockwise.inventory.domain;

import com.stockwise.business.domain.Business;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "business_inventory_settings")
public class BusinessInventorySettings {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "business_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID businessId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(
            name = "near_expiry_days",
            nullable = false,
            columnDefinition = "SMALLINT UNSIGNED")
    private short nearExpiryDays;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected BusinessInventorySettings() {
    }

    private BusinessInventorySettings(Business business, int nearExpiryDays) {
        this.business = business;
        this.nearExpiryDays = (short) nearExpiryDays;
    }

    public static BusinessInventorySettings create(Business business, int nearExpiryDays) {
        return new BusinessInventorySettings(business, nearExpiryDays);
    }

    public void setNearExpiryDays(int nearExpiryDays) {
        this.nearExpiryDays = (short) nearExpiryDays;
    }

    @PrePersist
    void setCreationTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public Business getBusiness() {
        return business;
    }

    public short getNearExpiryDays() {
        return nearExpiryDays;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
