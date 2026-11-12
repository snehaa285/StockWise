package com.stockwise.excelsync.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.Business;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.excelsync.domain.ExcelSyncOutbox;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExcelSyncWorkerTest {
    private final ExcelSyncQueueService queueService = mock(ExcelSyncQueueService.class);
    private final MicrosoftConnectionService connectionService =
            mock(MicrosoftConnectionService.class);
    private final MicrosoftGraphClient graphClient = mock(MicrosoftGraphClient.class);
    private final StockWiseWorkbookGenerator workbookGenerator =
            mock(StockWiseWorkbookGenerator.class);
    private final BusinessRepository businessRepository = mock(BusinessRepository.class);
    private final ExcelSyncWorker worker = new ExcelSyncWorker(
            queueService,
            connectionService,
            graphClient,
            workbookGenerator,
            businessRepository);

    private final UUID businessId = UUID.randomUUID();
    private final UUID outboxId = UUID.randomUUID();
    private final ExcelSyncOutbox outbox = mock(ExcelSyncOutbox.class);
    private final Business business = mock(Business.class);

    @BeforeEach
    void setUp() {
        when(outbox.getId()).thenReturn(outboxId);
        when(outbox.getBusiness()).thenReturn(business);
        when(business.getId()).thenReturn(businessId);
        when(business.getName()).thenReturn("Test Business");
        when(queueService.claimNext()).thenReturn(outbox);
    }

    @Test
    void uploadsWorkbookAndMarksClaimedRecordSucceeded() {
        byte[] workbook = {1, 2, 3};
        when(connectionService.accessToken(businessId)).thenReturn("access-token");
        when(workbookGenerator.generate(businessId)).thenReturn(workbook);
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(connectionService.driveItemId(businessId)).thenReturn("drive-item");
        when(graphClient.uploadWorkbook(
                org.mockito.ArgumentMatchers.eq("access-token"),
                org.mockito.ArgumentMatchers.eq("drive-item"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.same(workbook)))
                .thenReturn("uploaded-item");

        worker.synchronizeNext();

        verify(connectionService).markSynchronized(businessId, "uploaded-item");
        verify(queueService).markSucceeded(outboxId);
        verify(queueService, never()).markFailed(
                org.mockito.ArgumentMatchers.eq(outboxId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void marksRetryableUploadFailureForQueueRetry() {
        byte[] workbook = {1, 2, 3};
        when(connectionService.accessToken(businessId)).thenReturn("access-token");
        when(workbookGenerator.generate(businessId)).thenReturn(workbook);
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(connectionService.driveItemId(businessId)).thenReturn("drive-item");
        when(graphClient.uploadWorkbook(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new MicrosoftIntegrationException("Temporary upload failure.", true));

        worker.synchronizeNext();

        verify(queueService).markFailed(outboxId, "Temporary upload failure.", true);
        verify(queueService, never()).markSucceeded(outboxId);
    }

    @Test
    void leavesClaimForLeaseBasedRecoveryWhenWorkerCrashes() {
        when(connectionService.accessToken(businessId)).thenThrow(new AssertionError("crash"));

        assertThrows(AssertionError.class, worker::synchronizeNext);

        verify(queueService, never()).markSucceeded(outboxId);
        verify(queueService, never()).markFailed(
                org.mockito.ArgumentMatchers.eq(outboxId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void leavesClaimForLeaseRecoveryWhenBusinessDisappears() {
        when(connectionService.accessToken(businessId)).thenReturn("access-token");
        when(workbookGenerator.generate(businessId)).thenReturn(new byte[] {1});
        when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

        assertThrows(BusinessNotFoundException.class, worker::synchronizeNext);

        verify(queueService, never()).markSucceeded(outboxId);
        verify(queueService, never()).markFailed(
                org.mockito.ArgumentMatchers.eq(outboxId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void doesNothingWhenNoOutboxRecordIsAvailable() {
        when(queueService.claimNext()).thenReturn(null);

        worker.synchronizeNext();

        verify(connectionService, never()).accessToken(businessId);
        verify(queueService, never()).markSucceeded(outboxId);
    }
}
