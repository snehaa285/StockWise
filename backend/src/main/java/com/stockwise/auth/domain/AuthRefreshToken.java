package com.stockwise.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "auth_refresh_token")
public class AuthRefreshToken {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AuthSession session;

    @Column(
            name = "token_hash",
            nullable = false,
            length = 64,
            unique = true,
            columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime consumedAt;

    @Column(name = "revoked_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime revokedAt;

    protected AuthRefreshToken() {
    }

    private AuthRefreshToken(
            UUID id,
            AuthSession session,
            String tokenHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt) {
        this.id = id;
        this.session = session;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static AuthRefreshToken issue(
            AuthSession session,
            String tokenHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt) {
        return new AuthRefreshToken(
                UUID.randomUUID(), session, tokenHash, issuedAt, expiresAt);
    }

    public void consume(LocalDateTime consumedTime) {
        consumedAt = consumedTime;
    }

    public void revoke(LocalDateTime revokedTime) {
        if (revokedAt == null) {
            revokedAt = revokedTime;
        }
    }

    public UUID getId() {
        return id;
    }

    public AuthSession getSession() {
        return session;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
