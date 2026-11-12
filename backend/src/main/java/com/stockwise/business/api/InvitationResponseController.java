package com.stockwise.business.api;

import com.stockwise.business.application.BusinessInvitationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@Profile("mysql")
public class InvitationResponseController {
    private final BusinessInvitationService invitationService;

    public InvitationResponseController(BusinessInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/accept")
    public ResponseEntity<Void> accept(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody InvitationTokenRequest request) {
        invitationService.accept(userId(authentication), request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/decline")
    public ResponseEntity<Void> decline(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody InvitationTokenRequest request) {
        invitationService.decline(userId(authentication), request.token());
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
