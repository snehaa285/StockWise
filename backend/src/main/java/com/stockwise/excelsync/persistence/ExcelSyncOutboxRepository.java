package com.stockwise.excelsync.persistence;

import com.stockwise.excelsync.domain.ExcelSyncOutbox;
import com.stockwise.excelsync.domain.ExcelSyncStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExcelSyncOutboxRepository extends JpaRepository<ExcelSyncOutbox, UUID> {
    long countByBusiness_IdAndStatus(UUID businessId, ExcelSyncStatus status);

    boolean existsByBusiness_IdAndEventKey(UUID businessId, String eventKey);

    List<ExcelSyncOutbox> findAllByBusiness_IdAndStatus(
            UUID businessId, ExcelSyncStatus status);

    Optional<ExcelSyncOutbox> findFirstByBusiness_IdAndStatusOrderByCreatedAtDesc(
            UUID businessId, ExcelSyncStatus status);

    Optional<ExcelSyncOutbox> findFirstByBusiness_IdAndStatusOrderByCreatedAtAsc(
            UUID businessId, ExcelSyncStatus status);

    @Query(value = """
            SELECT outbox.*
            FROM excel_sync_outbox outbox
            JOIN microsoft_onedrive_connection connection
              ON connection.business_id = outbox.business_id
             AND connection.authorization_status = 'CONNECTED'
            WHERE (outbox.status = 'PENDING' AND outbox.next_attempt_at <= UTC_TIMESTAMP(6))
               OR (outbox.status = 'PROCESSING'
                   AND outbox.locked_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE))
            ORDER BY outbox.created_at, outbox.id
            LIMIT 1
            """, nativeQuery = true)
    Optional<ExcelSyncOutbox> findNextReady();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE excel_sync_outbox
            SET status = 'PROCESSING',
                locked_at = UTC_TIMESTAMP(6),
                last_error = NULL
            WHERE id = :id
              AND (
                    (status = 'PENDING' AND next_attempt_at <= UTC_TIMESTAMP(6))
                    OR (status = 'PROCESSING'
                        AND locked_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE))
              )
            """, nativeQuery = true)
    int claimIfReady(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outbox from ExcelSyncOutbox outbox "
            + "where outbox.id = :id and outbox.business.id = :businessId")
    Optional<ExcelSyncOutbox> findByBusinessAndIdForUpdate(
            @Param("businessId") UUID businessId, @Param("id") UUID id);
}
