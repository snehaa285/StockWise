package com.stockwise.inventory.api;

import com.stockwise.inventory.application.InventoryIntelligenceService;
import com.stockwise.inventory.application.InventoryReorderLevelService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/inventory")
@Profile("mysql")
public class InventoryIntelligenceController {
    private final InventoryIntelligenceService intelligenceService;
    private final InventoryReorderLevelService reorderLevelService;

    public InventoryIntelligenceController(
            InventoryIntelligenceService intelligenceService,
            InventoryReorderLevelService reorderLevelService) {
        this.intelligenceService = intelligenceService;
        this.reorderLevelService = reorderLevelService;
    }

    @GetMapping("/reorder-levels")
    public List<InventoryReorderLevelResponse> reorderLevels(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return reorderLevelService.list(businessId, authenticatedUserId(authentication));
    }

    @PutMapping("/reorder-levels")
    public InventoryReorderLevelResponse saveReorderLevel(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @Valid @RequestBody SaveReorderLevelRequest request) {
        return reorderLevelService.save(
                businessId, authenticatedUserId(authentication), request);
    }

    @DeleteMapping("/reorder-levels/{productId}/{locationId}")
    public ResponseEntity<Void> deleteReorderLevel(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID productId,
            @PathVariable UUID locationId) {
        reorderLevelService.delete(
                businessId, authenticatedUserId(authentication), productId, locationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/alerts/low-stock")
    public List<LowStockAlertResponse> lowStockAlerts(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return intelligenceService.lowStockAlerts(
                businessId, authenticatedUserId(authentication));
    }

    @GetMapping("/alerts/expiry")
    public ExpiryMonitoringResponse expiryMonitoring(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return intelligenceService.expiryMonitoring(
                businessId, authenticatedUserId(authentication));
    }

    @GetMapping("/settings")
    public BusinessInventorySettingsResponse settings(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return intelligenceService.settings(
                businessId, authenticatedUserId(authentication));
    }

    @PutMapping("/settings")
    public BusinessInventorySettingsResponse updateSettings(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @Valid @RequestBody UpdateBusinessInventorySettingsRequest request) {
        return intelligenceService.updateSettings(
                businessId, authenticatedUserId(authentication), request);
    }

    @GetMapping("/analytics/summary")
    public InventoryAnalyticsSummaryResponse summary(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return intelligenceService.summary(
                businessId, authenticatedUserId(authentication));
    }

    @GetMapping("/analytics/movements")
    public InventoryMovementTrendsResponse movementTrends(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return intelligenceService.movementTrends(
                businessId, authenticatedUserId(authentication), fromDate, toDate);
    }

    @GetMapping("/reports/inventory")
    public InventoryStockReportResponse stockReport(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return intelligenceService.stockReport(
                businessId, authenticatedUserId(authentication), page, size);
    }

    @GetMapping("/replenishment/suggestions")
    public List<LowStockAlertResponse> replenishmentSuggestions(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return intelligenceService.lowStockAlerts(
                businessId, authenticatedUserId(authentication));
    }

    private UUID authenticatedUserId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
