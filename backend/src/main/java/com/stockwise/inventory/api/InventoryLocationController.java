package com.stockwise.inventory.api;

import com.stockwise.inventory.application.InventoryLocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/locations")
@Profile("mysql")
public class InventoryLocationController {
    private final InventoryLocationService locationService;

    public InventoryLocationController(InventoryLocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ResponseEntity<InventoryLocationResponse> create(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @Valid @RequestBody SaveLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                locationService.create(businessId, authenticatedUserId(authentication), request));
    }

    @GetMapping
    public List<InventoryLocationResponse> list(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return locationService.list(businessId, authenticatedUserId(authentication));
    }

    @GetMapping("/{locationId}")
    public InventoryLocationResponse get(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID locationId) {
        return locationService.get(
                businessId, authenticatedUserId(authentication), locationId);
    }

    @PatchMapping("/{locationId}")
    public InventoryLocationResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID locationId,
            @Valid @RequestBody SaveLocationRequest request) {
        return locationService.update(
                businessId,
                authenticatedUserId(authentication),
                locationId,
                request);
    }

    @DeleteMapping("/{locationId}")
    public ResponseEntity<Void> archive(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID locationId) {
        locationService.archive(businessId, authenticatedUserId(authentication), locationId);
        return ResponseEntity.noContent().build();
    }

    private UUID authenticatedUserId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
