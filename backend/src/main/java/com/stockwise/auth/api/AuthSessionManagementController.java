package com.stockwise.auth.api;

import com.stockwise.auth.application.AuthLoginAuditService;
import com.stockwise.auth.application.AuthSessionManagementService;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("mysql")
public class AuthSessionManagementController {
    private final AuthSessionManagementService sessionService;
    private final AuthLoginAuditService auditService;
    private final AuthCookieService cookieService;

    public AuthSessionManagementController(
            AuthSessionManagementService sessionService,
            AuthLoginAuditService auditService,
            AuthCookieService cookieService) {
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.cookieService = cookieService;
    }

    @GetMapping("/sessions")
    public List<AuthSessionResponse> sessions(JwtAuthenticationToken authentication) {
        UUID userId = userId(authentication);
        UUID currentSessionId = sessionId(authentication);
        return sessionService.activeSessions(userId).stream()
                .map(session -> AuthSessionResponse.from(session, currentSessionId))
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revoke(
            JwtAuthenticationToken authentication, @PathVariable UUID sessionId) {
        sessionService.revoke(userId(authentication), sessionId);
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (sessionId.equals(sessionId(authentication))) {
            response.header(HttpHeaders.SET_COOKIE, cookieService.clearRefreshCookie().toString());
        }
        return response.build();
    }

    @PostMapping("/sessions/revoke-others")
    public RevokedSessionsResponse revokeOthers(JwtAuthenticationToken authentication) {
        int count = sessionService.revokeOthers(
                userId(authentication), sessionId(authentication));
        return new RevokedSessionsResponse(count);
    }

    @GetMapping("/login-history")
    public List<AuthLoginHistoryResponse> loginHistory(
            JwtAuthenticationToken authentication) {
        return auditService.recentHistory(userId(authentication)).stream()
                .map(AuthLoginHistoryResponse::from)
                .toList();
    }

    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }

    private UUID sessionId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getClaimAsString("sid"));
    }

    public record RevokedSessionsResponse(int revokedCount) {
    }
}
