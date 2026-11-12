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
public class RefreshTransactionService {
    private static final String REPLAY_REVOCATION_REASON = "TOKEN_REUSE";

    private final AuthSessionRepository authSessionRepository;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final RefreshTokenFactory refreshTokenFactory;
    private final JwtTokenService jwtTokenService;

    public RefreshTransactionService(
            AuthSessionRepository authSessionRepository,
            AuthRefreshTokenRepository authRefreshTokenRepository,
            RefreshTokenFactory refreshTokenFactory,
            JwtTokenService jwtTokenService) {
        this.authSessionRepository = authSessionRepository;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
        this.refreshTokenFactory = refreshTokenFactory;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public RefreshOutcome rotate(UUID sessionId, String tokenHash) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Optional<AuthSession> sessionResult = authSessionRepository.findByIdForUpdate(sessionId);
        if (sessionResult.isEmpty()) {
            return RefreshOutcome.unauthorized();
        }
        AuthSession session = sessionResult.get();

        Optional<AuthRefreshToken> tokenResult =
                authRefreshTokenRepository.findByTokenHashForUpdate(tokenHash);
        if (tokenResult.isEmpty()) {
            return RefreshOutcome.unauthorized();
        }
        AuthRefreshToken token = tokenResult.get();

        if (!token.getSession().getId().equals(session.getId())) {
            return RefreshOutcome.unauthorized();
        }
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            return RefreshOutcome.unauthorized();
        }
        if (token.getConsumedAt() != null || token.getRevokedAt() != null) {
            revokeFamily(session, now);
            return RefreshOutcome.replayed();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            return RefreshOutcome.unauthorized();
        }

        token.consume(now);
        RefreshTokenFactory.GeneratedRefreshToken replacement = refreshTokenFactory.generate();
        LocalDateTime replacementExpiry = min(now.plusDays(7), session.getExpiresAt());
        authRefreshTokenRepository.save(AuthRefreshToken.issue(
                session, replacement.hash(), now, replacementExpiry));
        session.markRotated(now);

        AuthSession userSession = authSessionRepository.findById(sessionId).orElseThrow();
        JwtTokenService.IssuedAccessToken accessToken =
                jwtTokenService.issue(userSession.getUser(), sessionId);
        return RefreshOutcome.rotated(accessToken, replacement.value(), replacementExpiry);
    }

    private void revokeFamily(AuthSession session, LocalDateTime now) {
        session.revoke(now, REPLAY_REVOCATION_REASON);
        List<AuthRefreshToken> family = authRefreshTokenRepository.findAllBySession_Id(session.getId());
        family.forEach(token -> token.revoke(now));
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    public record RefreshOutcome(
            Status status,
            JwtTokenService.IssuedAccessToken accessToken,
            String refreshToken,
            LocalDateTime refreshTokenExpiresAt) {
        public static RefreshOutcome unauthorized() {
            return new RefreshOutcome(Status.UNAUTHORIZED, null, null, null);
        }

        public static RefreshOutcome replayed() {
            return new RefreshOutcome(Status.REPLAYED, null, null, null);
        }

        private static RefreshOutcome rotated(
                JwtTokenService.IssuedAccessToken accessToken,
                String refreshToken,
                LocalDateTime refreshTokenExpiresAt) {
            return new RefreshOutcome(
                    Status.ROTATED, accessToken, refreshToken, refreshTokenExpiresAt);
        }

        public enum Status {
            ROTATED,
            REPLAYED,
            UNAUTHORIZED
        }
    }
}
