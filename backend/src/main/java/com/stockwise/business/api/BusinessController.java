package com.stockwise.business.api;

import com.stockwise.business.application.BusinessService;
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
@RequestMapping("/api/v1/businesses")
@Profile("mysql")
public class BusinessController {
    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @PostMapping
    public ResponseEntity<BusinessResponse> create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateBusinessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(businessService.create(authenticatedUserId(authentication), request));
    }

    @GetMapping
    public List<BusinessResponse> list(JwtAuthenticationToken authentication) {
        return businessService.listForUser(authenticatedUserId(authentication));
    }

    @GetMapping("/{businessId}")
    public BusinessResponse get(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return businessService.get(businessId, authenticatedUserId(authentication));
    }

    @PatchMapping("/{businessId}")
    public BusinessResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @Valid @RequestBody UpdateBusinessRequest request) {
        return businessService.update(
                businessId, authenticatedUserId(authentication), request);
    }

    @GetMapping("/{businessId}/members")
    public List<BusinessMemberResponse> members(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return businessService.listMembers(businessId, authenticatedUserId(authentication));
    }

    @PatchMapping("/{businessId}/members/{memberId}")
    public BusinessMemberResponse changeRole(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID memberId,
            @Valid @RequestBody BusinessRoleRequest request) {
        return businessService.changeMemberRole(
                businessId,
                authenticatedUserId(authentication),
                memberId,
                request.role());
    }

    @DeleteMapping("/{businessId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID memberId) {
        businessService.removeMember(
                businessId, authenticatedUserId(authentication), memberId);
        return ResponseEntity.noContent().build();
    }

    private UUID authenticatedUserId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
