package com.stockwise.excelsync.application;

import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.Business;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.excelsync.domain.ExcelSyncOutbox;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
public class ExcelSyncWorker {
    private final ExcelSyncQueueService queueService;
    private final MicrosoftConnectionService connectionService;
    private final MicrosoftGraphClient graphClient;
    private final StockWiseWorkbookGenerator workbookGenerator;
    private final BusinessRepository businessRepository;

    public ExcelSyncWorker(
            ExcelSyncQueueService queueService,
            MicrosoftConnectionService connectionService,
            MicrosoftGraphClient graphClient,
            StockWiseWorkbookGenerator workbookGenerator,
            BusinessRepository businessRepository) {
        this.queueService = queueService;
        this.connectionService = connectionService;
        this.graphClient = graphClient;
        this.workbookGenerator = workbookGenerator;
        this.businessRepository = businessRepository;
    }

    @Scheduled(fixedDelayString = "${stockwise.excel-sync.poll-interval-ms:5000}")
    public void synchronizeNext() {
        ExcelSyncOutbox outbox = queueService.claimNext();
        if (outbox == null) {
            return;
        }

        var businessId = outbox.getBusiness().getId();
        try {
            String accessToken = connectionService.accessToken(businessId);
            byte[] workbook = workbookGenerator.generate(businessId);
            Business business = businessRepository.findById(businessId)
                    .orElseThrow(BusinessNotFoundException::new);
            String filename = workbookFilename(business.getName(), businessId);
            String itemId = graphClient.uploadWorkbook(
                    accessToken,
                    connectionService.driveItemId(businessId),
                    filename,
                    workbook);
            connectionService.markSynchronized(businessId, itemId);
            queueService.markSucceeded(outbox.getId());
        } catch (MicrosoftReauthorizationRequiredException exception) {
            connectionService.markReauthorizationRequired(businessId);
            queueService.markFailed(
                    outbox.getId(),
                    "Microsoft authorization expired. Reconnect OneDrive to continue.",
                    false);
        } catch (MicrosoftIntegrationException exception) {
            queueService.markFailed(
                    outbox.getId(), exception.getMessage(), exception.isRetryable());
        } catch (DataAccessException | IllegalStateException exception) {
            queueService.markFailed(
                    outbox.getId(),
                    "StockWise could not prepare or save the Excel workbook.",
                    true);
        }
    }

    private static String workbookFilename(String businessName, java.util.UUID businessId) {
        String safeName = businessName == null
                ? "Business"
                : businessName.replaceAll("[^A-Za-z0-9 _.-]", "_").trim();
        if (safeName.isBlank()) {
            safeName = "Business";
        }
        if (safeName.length() > 100) {
            safeName = safeName.substring(0, 100).trim();
        }
        return "StockWise - " + safeName + " (" + businessId + ").xlsx";
    }
}
