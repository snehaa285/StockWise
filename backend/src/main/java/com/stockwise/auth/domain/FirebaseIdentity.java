package com.stockwise.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "auth_firebase_identity",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_auth_firebase_identity_user", columnNames = "user_id"),
            @UniqueConstraint(name = "uk_auth_firebase_identity_uid", columnNames = "firebase_uid")
        })
public class FirebaseIdentity {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "firebase_uid", nullable = false, length = 128, unique = true)
    private String firebaseUid;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected FirebaseIdentity() {
    }

    private FirebaseIdentity(
            UUID id, AppUser user, String firebaseUid, LocalDateTime createdAt) {
        this.id = id;
        this.user = user;
        this.firebaseUid = firebaseUid;
        this.createdAt = createdAt;
    }

    public static FirebaseIdentity link(
            AppUser user, String firebaseUid, LocalDateTime createdAt) {
        return new FirebaseIdentity(UUID.randomUUID(), user, firebaseUid, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
