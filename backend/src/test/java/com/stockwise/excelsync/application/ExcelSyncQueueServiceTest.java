package com.stockwise.excelsync.application;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
