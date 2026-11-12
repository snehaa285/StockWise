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
@Table(name = "business_invitation")
public class BusinessInvitation {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private BusinessRole role;

    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BusinessInvitationStatus status;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "responded_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime respondedAt;

    protected BusinessInvitation() {
    }

    private BusinessInvitation(
            Business business,
            String emailNormalized,
            BusinessRole role,
            String tokenHash,
            LocalDateTime expiresAt,
            AppUser createdBy) {
        this.id = UUID.randomUUID();
        this.business = business;
        this.emailNormalized = emailNormalized;
        this.role = role;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.status = BusinessInvitationStatus.PENDING;
    }

    public static BusinessInvitation create(
            Business business,
            String emailNormalized,
            BusinessRole role,
            String tokenHash,
            LocalDateTime expiresAt,
            AppUser createdBy) {
        return new BusinessInvitation(
                business, emailNormalized, role, tokenHash, expiresAt, createdBy);
    }

    public void replaceToken(String tokenHash, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public void markExpired() {
        respond(BusinessInvitationStatus.EXPIRED);
    }

    public void accept() {
        respond(BusinessInvitationStatus.ACCEPTED);
    }

    public void decline() {
        respond(BusinessInvitationStatus.DECLINED);
    }

    public void revoke() {
        respond(BusinessInvitationStatus.REVOKED);
    }

    private void respond(BusinessInvitationStatus nextStatus) {
        if (status != BusinessInvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can be changed.");
        }
        status = nextStatus;
        respondedAt = LocalDateTime.now(ZoneOffset.UTC);
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

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public BusinessRole getRole() {
        return role;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public BusinessInvitationStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }
}
