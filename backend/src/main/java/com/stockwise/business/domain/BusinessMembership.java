package com.stockwise.business.domain;

import com.stockwise.auth.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "business_membership")
public class BusinessMembership {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private BusinessRole role;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "removed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime removedAt;

    protected BusinessMembership() {
    }

    private BusinessMembership(
            UUID id, Business business, AppUser user, BusinessRole role) {
        this.id = id;
        this.business = business;
        this.user = user;
        this.role = role;
    }

    public static BusinessMembership create(
            Business business, AppUser user, BusinessRole role) {
        return new BusinessMembership(UUID.randomUUID(), business, user, role);
    }

    public void changeRole(BusinessRole role) {
        this.role = role;
    }

    public void remove(LocalDateTime removedAt) {
        this.removedAt = removedAt;
    }

    @PrePersist
    void setCreationTimestamp() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Business getBusiness() {
        return business;
    }

    public AppUser getUser() {
        return user;
    }

    public BusinessRole getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getRemovedAt() {
        return removedAt;
    }

    public boolean isActive() {
        return removedAt == null;
    }
}
