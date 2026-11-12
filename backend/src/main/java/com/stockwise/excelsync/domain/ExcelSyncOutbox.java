package com.stockwise.excelsync.domain;

import com.stockwise.business.domain.Business;
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
@Table(name = "excel_sync_outbox")
public class ExcelSyncOutbox {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "event_key", nullable = false, length = 128)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExcelSyncStatus status;

    @Column(name = "attempt_count", nullable = false, columnDefinition = "INT UNSIGNED")
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "locked_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lockedAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime completedAt;

    protected ExcelSyncOutbox() {
    }

    private ExcelSyncOutbox(Business business, String eventKey, LocalDateTime now) {
        this.id = UUID.randomUUID();
        this.business = business;
        this.eventKey = eventKey;
        this.status = ExcelSyncStatus.PENDING;
        this.nextAttemptAt = now;
    }

    public static ExcelSyncOutbox pending(Business business, String eventKey) {
        return new ExcelSyncOutbox(business, eventKey, now());
    }

    public void claim() {
        this.status = ExcelSyncStatus.PROCESSING;
        this.lockedAt = now();
        this.lastError = null;
    }

    public void succeeded() {
        this.status = ExcelSyncStatus.SUCCEEDED;
        this.completedAt = now();
        this.lockedAt = null;
        this.lastError = null;
    }

    public void failed(String safeError, boolean retryable, LocalDateTime nextAttemptAt) {
        this.attemptCount++;
        this.lastError = safeError;
        this.lockedAt = null;
        this.status = retryable ? ExcelSyncStatus.PENDING : ExcelSyncStatus.FAILED;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void retryNow() {
        if (status == ExcelSyncStatus.FAILED) {
            status = ExcelSyncStatus.PENDING;
            attemptCount = 0;
        }
        if (status == ExcelSyncStatus.PENDING) {
            nextAttemptAt = now();
            lockedAt = null;
            lastError = null;
        }
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Business getBusiness() {
        return business;
    }

    public String getEventKey() {
        return eventKey;
    }

    public ExcelSyncStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
