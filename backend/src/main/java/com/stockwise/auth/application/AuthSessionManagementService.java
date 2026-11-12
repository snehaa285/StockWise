package com.stockwise.auth.application;

import com.stockwise.auth.domain.AuthRefreshToken;
import com.stockwise.auth.domain.AuthSession;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class AuthSessionManagementService {
    private static final String USER_REVOCATION_REASON = "USER_REVOKED";

    private final AuthSessionRepository sessionRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final AuthLoginAuditService loginAuditService;

    public AuthSessionManagementService(
            AuthSessionRepository sessionRepository,
            AuthRefreshTokenRepository refreshTokenRepository,
            AuthLoginAuditService loginAuditService) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAuditService = loginAuditService;
    }

    @Transactional(readOnly = true)
    public List<AuthSession> activeSessions(UUID userId) {
        return sessionRepository
                .findAllByUser_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        userId, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        AuthSession session = sessionRepository.findByIdForUpdate(sessionId)
                .filter(candidate -> candidate.getUser().getId().equals(userId))
                .filter(this::isActive)
                .orElseThrow(AuthSessionNotFoundException::new);
        revokeSession(session);
    }

    @Transactional
    public int revokeOthers(UUID userId, UUID currentSessionId) {
        List<AuthSession> sessions = sessionRepository.findActiveByUserForUpdate(
                userId, LocalDateTime.now(ZoneOffset.UTC));
        int revoked = 0;
        for (AuthSession session : sessions) {
            if (!session.getId().equals(currentSessionId)) {
                revokeSession(session);
                revoked++;
            }
        }
        return revoked;
    }

    private boolean isActive(AuthSession session) {
        return session.getRevokedAt() == null
                && session.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    private void revokeSession(AuthSession session) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        session.revoke(now, USER_REVOCATION_REASON);
        List<AuthRefreshToken> tokens =
                refreshTokenRepository.findAllBySession_Id(session.getId());
        tokens.forEach(token -> token.revoke(now));
        loginAuditService.recordSessionRevoked(session);
    }
}
