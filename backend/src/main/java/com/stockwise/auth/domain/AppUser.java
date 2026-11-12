package com.stockwise.auth.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "app_user",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_app_user_email_normalized",
                columnNames = "email_normalized"))
public class AppUser {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected AppUser() {
    }

    private AppUser(
            UUID id,
            String displayName,
            String emailNormalized,
            String passwordHash) {
        this.id = id;
        this.displayName = displayName;
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
    }

    public static AppUser register(String displayName, String emailNormalized, String passwordHash) {
        return new AppUser(UUID.randomUUID(), displayName, emailNormalized, passwordHash);
    }

    public static AppUser registerFederated(String displayName, String emailNormalized) {
        return new AppUser(UUID.randomUUID(), displayName, emailNormalized, null);
    }

    @PrePersist
    void setCreationTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
