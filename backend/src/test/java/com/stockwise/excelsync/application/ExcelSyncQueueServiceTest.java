package com.stockwise.excelsync.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.domain.Business;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.excelsync.domain.ExcelSyncOutbox;
import com.stockwise.excelsync.domain.ExcelSyncStatus;
import com.stockwise.excelsync.domain.MicrosoftConnectionStatus;
import com.stockwise.excelsync.persistence.ExcelSyncOutboxRepository;
import com.stockwise.excelsync.persistence.MicrosoftOneDriveConnectionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class ExcelSyncQueueServiceTest {
    private final BusinessAccessService accessService =
            org.mockito.Mockito.mock(BusinessAccessService.class);
    private final BusinessRepository businessRepository =
            org.mockito.Mockito.mock(BusinessRepository.class);
    private final MicrosoftOneDriveConnectionRepository connectionRepository =
            org.mockito.Mockito.mock(MicrosoftOneDriveConnectionRepository.class);
    private final ExcelSyncOutboxRepository outboxRepository =
            org.mockito.Mockito.mock(ExcelSyncOutboxRepository.class);
    private final ExcelSyncQueueService service = new ExcelSyncQueueService(
            accessService, businessRepository, connectionRepository, outboxRepository);

    @Test
    void doesNotQueueChangesWhenOneDriveIsNotConnected() {
        UUID businessId = UUID.randomUUID();
        when(connectionRepository.existsByBusinessIdAndAuthorizationStatus(
                businessId, MicrosoftConnectionStatus.CONNECTED)).thenReturn(false);

        service.enqueueChange(businessId, "PRODUCT");

        verify(outboxRepository, never()).save(any(ExcelSyncOutbox.class));
    }

    @Test
    void coalescesNewChangesIntoAnExistingPendingSnapshot() {
        UUID businessId = UUID.randomUUID();
        ExcelSyncOutbox pending = org.mockito.Mockito.mock(ExcelSyncOutbox.class);
        when(connectionRepository.existsByBusinessIdAndAuthorizationStatus(
                businessId, MicrosoftConnectionStatus.CONNECTED)).thenReturn(true);
        when(businessRepository.findByIdForUpdate(businessId))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Business.class)));
        when(outboxRepository.findFirstByBusiness_IdAndStatusOrderByCreatedAtAsc(
                businessId, ExcelSyncStatus.PENDING)).thenReturn(Optional.of(pending));

        service.enqueueChange(businessId, "INVENTORY");

        verify(outboxRepository, never()).save(any(ExcelSyncOutbox.class));
    }

    @Test
    void claimsCandidateOnlyAfterConditionalUpdateSucceeds() {
        ExcelSyncOutbox candidate = org.mockito.Mockito.mock(ExcelSyncOutbox.class);
        UUID outboxId = UUID.randomUUID();
        when(candidate.getId()).thenReturn(outboxId);
        when(outboxRepository.findNextReady()).thenReturn(Optional.of(candidate));
        when(outboxRepository.claimIfReady(outboxId)).thenReturn(1);
        when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(candidate));

        assertSame(candidate, service.claimNext());

        verify(outboxRepository).claimIfReady(outboxId);
        verify(outboxRepository).findById(outboxId);
    }

    @Test
    void returnsNoClaimWhenAnotherWorkerWinsTheConditionalUpdate() {
        ExcelSyncOutbox candidate = org.mockito.Mockito.mock(ExcelSyncOutbox.class);
        UUID outboxId = UUID.randomUUID();
        when(candidate.getId()).thenReturn(outboxId);
        when(outboxRepository.findNextReady()).thenReturn(Optional.of(candidate));
        when(outboxRepository.claimIfReady(outboxId)).thenReturn(0);

        assertNull(service.claimNext());

        verify(outboxRepository, never()).findById(outboxId);
    }

    @Test
    void returnsNoClaimWhenNoEligibleRecordExists() {
        when(outboxRepository.findNextReady()).thenReturn(Optional.empty());

        assertNull(service.claimNext());

        verify(outboxRepository, never()).claimIfReady(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onlyOneConcurrentWorkerCanClaimTheSameCandidate() throws Exception {
        ExcelSyncOutbox candidate = org.mockito.Mockito.mock(ExcelSyncOutbox.class);
        UUID outboxId = UUID.randomUUID();
        when(candidate.getId()).thenReturn(outboxId);
        CountDownLatch bothSelected = new CountDownLatch(2);
        when(outboxRepository.findNextReady()).thenAnswer(invocation -> {
            bothSelected.countDown();
            if (!bothSelected.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Both workers did not select the candidate.");
            }
            return Optional.of(candidate);
        });
        AtomicBoolean claimed = new AtomicBoolean();
        when(outboxRepository.claimIfReady(outboxId))
                .thenAnswer(invocation -> claimed.compareAndSet(false, true) ? 1 : 0);
        when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(candidate));

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ExcelSyncOutbox> first = workers.submit(service::claimNext);
            Future<ExcelSyncOutbox> second = workers.submit(service::claimNext);

            int successfulClaims = (first.get(10, TimeUnit.SECONDS) == null ? 0 : 1)
                    + (second.get(10, TimeUnit.SECONDS) == null ? 0 : 1);
            assertEquals(1, successfulClaims);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void rollsBackClaimTransactionWhenReloadFails() {
        ExcelSyncOutbox candidate = org.mockito.Mockito.mock(ExcelSyncOutbox.class);
        UUID outboxId = UUID.randomUUID();
        when(candidate.getId()).thenReturn(outboxId);
        when(outboxRepository.findNextReady()).thenReturn(Optional.of(candidate));
        when(outboxRepository.claimIfReady(outboxId)).thenReturn(1);
        when(outboxRepository.findById(outboxId))
                .thenThrow(new IllegalStateException("database read failed"));

        PlatformTransactionManager transactionManager =
                org.mockito.Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus =
                org.mockito.Mockito.mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        ExcelSyncQueueService transactionalService =
                (ExcelSyncQueueService) proxyFactory.getProxy();

        assertThrows(IllegalStateException.class, transactionalService::claimNext);

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
    }

    @Test
    void reclaimsProcessingRowsOnlyAfterTheExistingLeaseExpires() throws Exception {
        Query candidateQuery = ExcelSyncOutboxRepository.class
                .getMethod("findNextReady")
                .getAnnotation(Query.class);
        Query claimQuery = ExcelSyncOutboxRepository.class
                .getMethod("claimIfReady", UUID.class)
                .getAnnotation(Query.class);

        assertTrue(candidateQuery.value().contains(
                "locked_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE)"));
        assertTrue(claimQuery.value().contains(
                "locked_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE)"));
        assertFalse(candidateQuery.value().contains("SKIP LOCKED"));
    }

    @Test
    void failedProcessingRetainsRetryBackoffAndAttemptLimit() {
        Business business = org.mockito.Mockito.mock(Business.class);
        ExcelSyncOutbox outbox = ExcelSyncOutbox.pending(business, "sync-event");
        UUID outboxId = outbox.getId();
        when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        service.markFailed(outboxId, "Temporary failure.", true);

        assertEquals(ExcelSyncStatus.PENDING, outbox.getStatus());
        assertEquals(1, outbox.getAttemptCount());
        assertNotNull(outbox.getNextAttemptAt());
        assertTrue(outbox.getNextAttemptAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));

        for (int attempt = 1; attempt < 11; attempt++) {
            outbox.failed(
                    "Temporary failure.",
                    true,
                    LocalDateTime.now().plusMinutes(1));
        }
        service.markFailed(outboxId, "Retry limit reached.", true);

        assertEquals(ExcelSyncStatus.FAILED, outbox.getStatus());
        assertEquals(12, outbox.getAttemptCount());
    }
}
