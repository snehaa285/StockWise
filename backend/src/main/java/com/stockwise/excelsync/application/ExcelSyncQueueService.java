package com.stockwise.excelsync.application;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.application.BusinessConflictException;
import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.Business;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.excelsync.api.ExcelSyncStatusResponse;
import com.stockwise.excelsync.domain.ExcelSyncOutbox;
import com.stockwise.excelsync.domain.ExcelSyncStatus;
import com.stockwise.excelsync.domain.MicrosoftConnectionStatus;
import com.stockwise.excelsync.domain.MicrosoftOneDriveConnection;
import com.stockwise.excelsync.persistence.ExcelSyncOutboxRepository;
import com.stockwise.excelsync.persistence.MicrosoftOneDriveConnectionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("mysql")
public class ExcelSyncQueueService {
    private static final int MAX_ATTEMPTS = 12;
    private static final long MAX_BACKOFF_SECONDS = 3600;

    private final BusinessAccessService businessAccessService;
    private final BusinessRepository businessRepository;
    private final MicrosoftOneDriveConnectionRepository connectionRepository;
    private final ExcelSyncOutboxRepository outboxRepository;

    public ExcelSyncQueueService(
            BusinessAccessService businessAccessService,
            BusinessRepository businessRepository,
            MicrosoftOneDriveConnectionRepository connectionRepository,
            ExcelSyncOutboxRepository outboxRepository) {
        this.businessAccessService = businessAccessService;
        this.businessRepository = businessRepository;
        this.connectionRepository = connectionRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueChange(UUID businessId, String eventType) {
        if (!connectionRepository.existsByBusinessIdAndAuthorizationStatus(
                businessId, MicrosoftConnectionStatus.CONNECTED)) {
            return;
        }
        enqueueUnlessPending(
                businessId, safeEventType(eventType) + ":" + UUID.randomUUID());
    }

    @Transactional
    public ExcelSyncStatusResponse status(UUID businessId, UUID actorId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        MicrosoftOneDriveConnection connection = connectionRepository.findById(businessId)
                .orElse(null);
        long pending = outboxRepository.countByBusiness_IdAndStatus(
                businessId, ExcelSyncStatus.PENDING)
                + outboxRepository.countByBusiness_IdAndStatus(
                        businessId, ExcelSyncStatus.PROCESSING);
        long failed = outboxRepository.countByBusiness_IdAndStatus(
                businessId, ExcelSyncStatus.FAILED);
        String status = connection == null
                ? MicrosoftConnectionStatus.DISCONNECTED.name()
                : connection.getAuthorizationStatus().name();
        if (connection != null
                && connection.getAuthorizationStatus() == MicrosoftConnectionStatus.CONNECTED) {
            if (failed > 0) {
                status = ExcelSyncStatus.FAILED.name();
            } else if (pending > 0) {
                status = ExcelSyncStatus.PROCESSING.name();
            }
        }
        String lastError = outboxRepository
                .findFirstByBusiness_IdAndStatusOrderByCreatedAtDesc(
                        businessId, ExcelSyncStatus.FAILED)
                .map(ExcelSyncOutbox::getLastError)
                .orElse(null);
        return new ExcelSyncStatusResponse(
                businessId,
                status,
                connection == null
                        || connection.getAuthorizationStatus()
                                == MicrosoftConnectionStatus.DISCONNECTED
                        ? null
                        : connection.getMicrosoftAccountEmail(),
                connection == null ? null : connection.getLastSyncedAt(),
                pending,
                failed,
                lastError);
    }

    @Transactional
    public int retryFailed(UUID businessId, UUID actorId) {
        businessAccessService.requireRole(businessId, actorId, BusinessRole.OWNER);
        requireConnected(businessId);
        var failed = outboxRepository.findAllByBusiness_IdAndStatus(
                businessId, ExcelSyncStatus.FAILED);
        failed.forEach(ExcelSyncOutbox::retryNow);
        if (!failed.isEmpty()) {
            outboxRepository.saveAll(failed);
            return failed.size();
        }
        enqueueResync(businessId);
        return 1;
    }

    @Transactional
    public void enqueueResync(UUID businessId, UUID actorId) {
        businessAccessService.requireRole(businessId, actorId, BusinessRole.OWNER);
        requireConnected(businessId);
        enqueueResync(businessId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ExcelSyncOutbox enqueueResync(UUID businessId) {
        if (!connectionRepository.existsByBusinessIdAndAuthorizationStatus(
                businessId, MicrosoftConnectionStatus.CONNECTED)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Connect OneDrive before requesting synchronization.");
        }
        return enqueueUnlessPending(businessId, "RESYNC:" + UUID.randomUUID());
    }

    @Transactional
    public ExcelSyncOutbox claimNext() {
        return outboxRepository.claimNextReady()
                .map(outbox -> {
                    outbox.claim();
                    return outboxRepository.save(outbox);
                })
                .orElse(null);
    }

    @Transactional
    public void markSucceeded(UUID outboxId) {
        ExcelSyncOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Sync outbox record disappeared."));
        outbox.succeeded();
    }

    @Transactional
    public void markFailed(UUID outboxId, String safeError, boolean retryable) {
        ExcelSyncOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Sync outbox record disappeared."));
        int nextAttempt = outbox.getAttemptCount() + 1;
        boolean willRetry = retryable && nextAttempt < MAX_ATTEMPTS;
        long delay = Math.min(
                MAX_BACKOFF_SECONDS,
                5L << Math.min(Math.max(0, nextAttempt - 1), 10));
        outbox.failed(
                safeError,
                willRetry,
                LocalDateTime.now(ZoneOffset.UTC).plusSeconds(delay));
    }

    @Transactional
    public UUID businessIdFor(UUID outboxId) {
        return outboxRepository.findById(outboxId)
                .map(outbox -> outbox.getBusiness().getId())
                .orElseThrow(() -> new IllegalStateException("Sync outbox record disappeared."));
    }

    private ExcelSyncOutbox enqueue(UUID businessId, String eventKey) {
        if (outboxRepository.existsByBusiness_IdAndEventKey(businessId, eventKey)) {
            return outboxRepository.findAllByBusiness_IdAndStatus(
                            businessId, ExcelSyncStatus.PENDING)
                    .stream()
                    .filter(item -> item.getEventKey().equals(eventKey))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Duplicate sync event was not available."));
        }
        Business business = businessRepository.findById(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        return outboxRepository.save(ExcelSyncOutbox.pending(business, eventKey));
    }

    private ExcelSyncOutbox enqueueUnlessPending(UUID businessId, String eventKey) {
        businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        return outboxRepository
                .findFirstByBusiness_IdAndStatusOrderByCreatedAtAsc(
                        businessId, ExcelSyncStatus.PENDING)
                .orElseGet(() -> enqueue(businessId, eventKey));
    }

    private void requireConnected(UUID businessId) {
        MicrosoftOneDriveConnection connection = connectionRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Connect OneDrive before retrying synchronization."));
        if (connection.getAuthorizationStatus() != MicrosoftConnectionStatus.CONNECTED) {
            throw new BusinessConflictException(
                    "Reconnect OneDrive before retrying synchronization.");
        }
    }

    private String safeEventType(String eventType) {
        if (eventType == null || !eventType.matches("[A-Z_]{1,48}")) {
            throw new IllegalArgumentException("Invalid Excel synchronization event type.");
        }
        return eventType;
    }
}
