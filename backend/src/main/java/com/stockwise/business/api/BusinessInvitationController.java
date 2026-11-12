package com.stockwise.business.api;

import com.stockwise.business.application.BusinessInvitationService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/invitations")
@Profile("mysql")
public class BusinessInvitationController {
    private final BusinessInvitationService invitationService;

    public BusinessInvitationController(BusinessInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    public ResponseEntity<BusinessInvitationResponse> invite(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateBusinessInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                invitationService.invite(businessId, userId(authentication), request));
    }

    @GetMapping
    public List<BusinessInvitationResponse> list(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return invitationService.list(businessId, userId(authentication));
    }

    @PostMapping("/{invitationId}/revoke")
    public ResponseEntity<Void> revoke(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID invitationId) {
        invitationService.revoke(businessId, userId(authentication), invitationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{invitationId}/resend")
    public BusinessInvitationResponse resend(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID invitationId) {
        return invitationService.resend(businessId, userId(authentication), invitationId);
    }

    private static UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
