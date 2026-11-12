package com.stockwise.excelsync.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExcelSyncStatusResponse(
        UUID businessId,
        String status,
        String connectedAccount,
        LocalDateTime lastSuccessfulSync,
        long pendingChanges,
        long failedChanges,
        String lastError) {
}
