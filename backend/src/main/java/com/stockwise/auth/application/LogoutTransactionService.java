package com.stockwise.auth.application;

import com.stockwise.auth.domain.AuthRefreshToken;
import com.stockwise.auth.domain.AuthSession;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class LogoutTransactionService {
    private static final String LOGOUT_REVOCATION_REASON = "USER_LOGOUT";

    private final AuthSessionRepository authSessionRepository;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;

    public LogoutTransactionService(
            AuthSessionRepository authSessionRepository,
            AuthRefreshTokenRepository authRefreshTokenRepository) {
        this.authSessionRepository = authSessionRepository;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
    }

    @Transactional
    public boolean revokeSessionFamily(UUID sessionId, String tokenHash) {
        Optional<AuthSession> sessionResult = authSessionRepository.findByIdForUpdate(sessionId);
        if (sessionResult.isEmpty()) {
            return false;
        }
        AuthSession session = sessionResult.get();

        Optional<AuthRefreshToken> tokenResult =
                authRefreshTokenRepository.findByTokenHashForUpdate(tokenHash);
        if (tokenResult.isEmpty()
                || !tokenResult.get().getSession().getId().equals(session.getId())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        session.revoke(now, LOGOUT_REVOCATION_REASON);
        List<AuthRefreshToken> family =
                authRefreshTokenRepository.findAllBySession_Id(session.getId());
        family.forEach(token -> token.revoke(now));
        return true;
    }
}
