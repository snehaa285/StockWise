package com.stockwise.excelsync.api;

import com.stockwise.excelsync.application.ExcelSyncQueueService;
import com.stockwise.excelsync.application.MicrosoftConnectionService;
import com.stockwise.excelsync.application.MicrosoftIntegrationException;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("mysql")
public class MicrosoftOneDriveController {
    private final MicrosoftConnectionService connectionService;
    private final ExcelSyncQueueService syncQueueService;

    public MicrosoftOneDriveController(
            MicrosoftConnectionService connectionService,
            ExcelSyncQueueService syncQueueService) {
        this.connectionService = connectionService;
        this.syncQueueService = syncQueueService;
    }

    @GetMapping("/api/v1/businesses/{businessId}/excel-sync")
    public ExcelSyncStatusResponse status(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return syncQueueService.status(businessId, authenticatedUserId(authentication));
    }

    @PostMapping("/api/v1/businesses/{businessId}/excel-sync/connect")
    public MicrosoftAuthorizationResponse connect(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return new MicrosoftAuthorizationResponse(
                connectionService.beginAuthorization(
                        businessId, authenticatedUserId(authentication)));
    }

    @DeleteMapping("/api/v1/businesses/{businessId}/excel-sync/connect")
    public ResponseEntity<Void> disconnect(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        connectionService.disconnect(businessId, authenticatedUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/businesses/{businessId}/excel-sync/retry")
    public RetrySyncResponse retry(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        int queued = syncQueueService.retryFailed(
                businessId, authenticatedUserId(authentication));
        return new RetrySyncResponse(queued);
    }

    @PostMapping("/api/v1/businesses/{businessId}/excel-sync/resynchronize")
    public ResponseEntity<Void> resynchronize(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        syncQueueService.enqueueResync(businessId, authenticatedUserId(authentication));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/v1/excel-sync/microsoft/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        String result = "failed";
        UUID businessId = null;
        if ("access_denied".equals(error)) {
            result = "denied";
        } else if (error == null || error.isBlank()) {
            try {
                businessId = connectionService.completeAuthorization(code, state);
                result = "connected";
            } catch (MicrosoftIntegrationException | ResponseStatusException exception) {
                result = "failed";
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(
                        connectionService.callbackRedirect(result, businessId)).toString())
                .build();
    }

    private UUID authenticatedUserId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
