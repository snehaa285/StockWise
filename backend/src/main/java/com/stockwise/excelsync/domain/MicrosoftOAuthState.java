package com.stockwise.excelsync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "microsoft_oauth_state")
public class MicrosoftOAuthState {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "business_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID businessId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "state_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String stateHash;

    @Column(name = "encrypted_code_verifier", nullable = false, columnDefinition = "TEXT")
    private String encryptedCodeVerifier;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected MicrosoftOAuthState() {
    }

    public static MicrosoftOAuthState create(
            UUID businessId,
            UUID userId,
            String stateHash,
            String encryptedCodeVerifier,
            LocalDateTime expiresAt) {
        MicrosoftOAuthState state = new MicrosoftOAuthState();
        state.id = UUID.randomUUID();
        state.businessId = businessId;
        state.userId = userId;
        state.stateHash = stateHash;
        state.encryptedCodeVerifier = encryptedCodeVerifier;
        state.expiresAt = expiresAt;
        state.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        return state;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getStateHash() {
        return stateHash;
    }

    public String getEncryptedCodeVerifier() {
        return encryptedCodeVerifier;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
