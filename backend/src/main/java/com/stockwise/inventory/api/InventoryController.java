package com.stockwise.inventory.api;

import com.stockwise.inventory.application.InventoryWriteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/inventory")
@Profile("mysql")
public class InventoryController {
    private final InventoryWriteService inventoryService;

    public InventoryController(InventoryWriteService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/receipts")
    public ResponseEntity<InventoryOperationResponse> receive(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReceiveStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                inventoryService.receive(
                        businessId, authenticatedUserId(authentication), idempotencyKey, request));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<InventoryOperationResponse> adjust(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                inventoryService.adjust(
                        businessId, authenticatedUserId(authentication), idempotencyKey, request));
    }

    @PostMapping("/transfers")
    public ResponseEntity<InventoryOperationResponse> transfer(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                inventoryService.transfer(
                        businessId, authenticatedUserId(authentication), idempotencyKey, request));
    }

    @GetMapping("/stock")
    public List<InventoryStockBalanceResponse> stock(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId) {
        return inventoryService.stock(
                businessId, authenticatedUserId(authentication), productId, locationId);
    }

    @GetMapping("/batches")
    public List<InventoryBatchResponse> batches(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestParam UUID productId) {
        return inventoryService.batches(
                businessId, authenticatedUserId(authentication), productId);
    }

    @GetMapping("/movements")
    public InventoryMovementPageResponse movements(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return inventoryService.movements(
                businessId,
                authenticatedUserId(authentication),
                productId,
                locationId,
                page,
                size);
    }

    private UUID authenticatedUserId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
