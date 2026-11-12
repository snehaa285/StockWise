package com.stockwise.catalog.api;

import com.stockwise.catalog.application.AttributeDefinitionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/categories/{categoryId}/attributes")
@Profile("mysql")
public class AttributeDefinitionController {
    private final AttributeDefinitionService service;

    public AttributeDefinitionController(AttributeDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<AttributeDefinitionResponse> list(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId) {
        return service.list(businessId, userId(authentication), categoryId);
    }

    @PostMapping
    public ResponseEntity<AttributeDefinitionResponse> create(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CreateAttributeDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(businessId, userId(authentication), categoryId, request));
    }

    @PutMapping("/{definitionId}")
    public AttributeDefinitionResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId,
            @PathVariable UUID definitionId,
            @Valid @RequestBody UpdateAttributeDefinitionRequest request) {
        return service.update(
                businessId, userId(authentication), categoryId, definitionId, request);
    }

    @DeleteMapping("/{definitionId}")
    public ResponseEntity<Void> archiveOrDelete(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId,
            @PathVariable UUID definitionId) {
        service.archiveOrDelete(
                businessId, userId(authentication), categoryId, definitionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{definitionId}/options")
    public ResponseEntity<AttributeDefinitionResponse> addOption(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId,
            @PathVariable UUID definitionId,
            @Valid @RequestBody AttributeOptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.addOption(
                        businessId, userId(authentication), categoryId, definitionId, request));
    }

    @PutMapping("/{definitionId}/options/{optionId}")
    public AttributeDefinitionResponse updateOption(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId,
            @PathVariable UUID definitionId,
            @PathVariable UUID optionId,
            @Valid @RequestBody AttributeOptionRequest request) {
        return service.updateOption(
                businessId,
                userId(authentication),
                categoryId,
                definitionId,
                optionId,
                request);
    }

    @DeleteMapping("/{definitionId}/options/{optionId}")
    public ResponseEntity<Void> deleteOption(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId,
            @PathVariable UUID definitionId,
            @PathVariable UUID optionId) {
        service.deleteOption(
                businessId, userId(authentication), categoryId, definitionId, optionId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
