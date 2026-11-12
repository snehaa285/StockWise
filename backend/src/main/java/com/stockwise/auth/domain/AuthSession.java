package com.stockwise.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_session")
public class AuthSession {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "last_rotated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastRotatedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 32)
    private String revocationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_method", nullable = false, length = 24)
    private AuthenticationMethod authenticationMethod;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected AuthSession() {
    }

    private AuthSession(
            UUID id,
            AppUser user,
            LocalDateTime createdAt,
            AuthenticationMethod authenticationMethod,
            String userAgent) {
        this.id = id;
        this.user = user;
        this.createdAt = createdAt;
        this.authenticationMethod = authenticationMethod;
        this.userAgent = userAgent;
        this.expiresAt = createdAt.plusDays(30);
    }

    public static AuthSession create(
            AppUser user,
            LocalDateTime createdAt,
            AuthenticationMethod authenticationMethod,
            String userAgent) {
        return new AuthSession(
                UUID.randomUUID(), user, createdAt, authenticationMethod, userAgent);
    }

    public void markRotated(LocalDateTime rotatedAt) {
        lastRotatedAt = rotatedAt;
    }

    public void revoke(LocalDateTime revokedTime, String reason) {
        if (revokedAt == null) {
            revokedAt = revokedTime;
            revocationReason = reason;
        }
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastRotatedAt() {
        return lastRotatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public AuthenticationMethod getAuthenticationMethod() {
        return authenticationMethod;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
