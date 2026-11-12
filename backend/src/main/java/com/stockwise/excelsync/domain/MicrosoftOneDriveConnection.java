package com.stockwise.excelsync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "microsoft_onedrive_connection")
public class MicrosoftOneDriveConnection {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "business_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID businessId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "connected_by_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID connectedByUserId;

    @Column(name = "microsoft_account_id", length = 128)
    private String microsoftAccountId;

    @Column(name = "microsoft_account_email", length = 254)
    private String microsoftAccountEmail;

    @Column(name = "encrypted_access_token", columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "access_token_expires_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime accessTokenExpiresAt;

    @Column(name = "drive_item_id", length = 512)
    private String driveItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_status", nullable = false, length = 24)
    private MicrosoftConnectionStatus authorizationStatus;

    @Column(name = "connected_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime connectedAt;

    @Column(name = "last_synced_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastSyncedAt;

    protected MicrosoftOneDriveConnection() {
    }

    public static MicrosoftOneDriveConnection create(
            UUID businessId,
            UUID connectedByUserId,
            String accountId,
            String accountEmail,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            LocalDateTime tokenExpiresAt,
            String driveItemId) {
        MicrosoftOneDriveConnection connection = new MicrosoftOneDriveConnection();
        connection.businessId = businessId;
        connection.connect(
                connectedByUserId,
                accountId,
                accountEmail,
                encryptedAccessToken,
                encryptedRefreshToken,
                tokenExpiresAt,
                driveItemId);
        return connection;
    }

    public void connect(
            UUID connectedByUserId,
            String accountId,
            String accountEmail,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            LocalDateTime tokenExpiresAt,
            String nextDriveItemId) {
        this.connectedByUserId = connectedByUserId;
        microsoftAccountId = accountId;
        microsoftAccountEmail = accountEmail;
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        accessTokenExpiresAt = tokenExpiresAt;
        driveItemId = nextDriveItemId;
        authorizationStatus = MicrosoftConnectionStatus.CONNECTED;
        connectedAt = now();
    }

    public void requireReauthorization() {
        authorizationStatus = MicrosoftConnectionStatus.REAUTH_REQUIRED;
    }

    public void disconnect() {
        authorizationStatus = MicrosoftConnectionStatus.DISCONNECTED;
        encryptedAccessToken = null;
        encryptedRefreshToken = null;
        accessTokenExpiresAt = null;
    }

    public void updateTokens(
            String encryptedAccessToken,
            String encryptedRefreshToken,
            LocalDateTime tokenExpiresAt) {
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        accessTokenExpiresAt = tokenExpiresAt;
        authorizationStatus = MicrosoftConnectionStatus.CONNECTED;
    }

    public void updateDriveItemId(String driveItemId) {
        this.driveItemId = driveItemId;
    }

    public void markSynchronized() {
        lastSyncedAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getConnectedByUserId() {
        return connectedByUserId;
    }

    public String getMicrosoftAccountId() {
        return microsoftAccountId;
    }

    public String getMicrosoftAccountEmail() {
        return microsoftAccountEmail;
    }

    public String getEncryptedAccessToken() {
        return encryptedAccessToken;
    }

    public String getEncryptedRefreshToken() {
        return encryptedRefreshToken;
    }

    public LocalDateTime getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public String getDriveItemId() {
        return driveItemId;
    }

    public MicrosoftConnectionStatus getAuthorizationStatus() {
        return authorizationStatus;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }
}
