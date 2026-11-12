package com.stockwise.auth.application;

import com.stockwise.auth.api.LoginResponse;
import com.stockwise.auth.api.RefreshResponse;
import com.stockwise.auth.api.UserResponse;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.domain.AuthRefreshToken;
import com.stockwise.auth.domain.AuthSession;
import com.stockwise.auth.domain.AuthenticationMethod;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class AuthSessionService {
    private final AuthSessionRepository authSessionRepository;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final RefreshTokenFactory refreshTokenFactory;
    private final JwtTokenService jwtTokenService;
    private final CsrfTokenService csrfTokenService;
    private final RefreshTransactionService refreshTransactionService;
    private final LogoutTransactionService logoutTransactionService;
    private final AuthLoginAuditService loginAuditService;

    public AuthSessionService(
            AuthSessionRepository authSessionRepository,
            AuthRefreshTokenRepository authRefreshTokenRepository,
            RefreshTokenFactory refreshTokenFactory,
            JwtTokenService jwtTokenService,
            CsrfTokenService csrfTokenService,
            RefreshTransactionService refreshTransactionService,
            LogoutTransactionService logoutTransactionService,
            AuthLoginAuditService loginAuditService) {
        this.authSessionRepository = authSessionRepository;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
        this.refreshTokenFactory = refreshTokenFactory;
        this.jwtTokenService = jwtTokenService;
        this.csrfTokenService = csrfTokenService;
        this.refreshTransactionService = refreshTransactionService;
        this.logoutTransactionService = logoutTransactionService;
        this.loginAuditService = loginAuditService;
    }

    @Transactional
    public LoginSessionResult createSession(AppUser user) {
        return createSession(user, AuthenticationMethod.EMAIL_PASSWORD, null);
    }

    @Transactional
    public LoginSessionResult createSession(
            AppUser user, AuthenticationMethod authenticationMethod, String userAgent) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String safeUserAgent = loginAuditService.sanitizeUserAgent(userAgent);
        AuthSession session = authSessionRepository.save(AuthSession.create(
                user, now, authenticationMethod, safeUserAgent));
        RefreshTokenFactory.GeneratedRefreshToken refreshToken = refreshTokenFactory.generate();
        LocalDateTime refreshExpiry = min(now.plusDays(7), session.getExpiresAt());
        authRefreshTokenRepository.save(AuthRefreshToken.issue(
                session, refreshToken.hash(), now, refreshExpiry));
        loginAuditService.recordLoginSuccess(session);

        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issue(user, session.getId());
        return new LoginSessionResult(
                new LoginResponse(
                        accessToken.value(),
                        "Bearer",
                        accessToken.expiresInSeconds(),
                        UserResponse.from(user)),
                refreshToken.value(),
                refreshExpiry);
    }

    @Transactional(readOnly = true)
    public Optional<String> csrfToken(String rawRefreshToken) {
        if (!refreshTokenFactory.isWellFormed(rawRefreshToken)) {
            return Optional.empty();
        }
        Optional<AuthRefreshToken> token =
                authRefreshTokenRepository.findByTokenHash(refreshTokenFactory.hash(rawRefreshToken));
        if (token.isEmpty()) {
            return Optional.empty();
        }

        AuthRefreshToken storedToken = token.get();
        AuthSession session = storedToken.getSession();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!isUsable(session, storedToken, now)) {
            return Optional.empty();
        }
        return Optional.of(csrfTokenService.create(session.getId()));
    }

    public RefreshTransactionService.RefreshOutcome refresh(
            String rawRefreshToken, String csrfToken) {
        if (!refreshTokenFactory.isWellFormed(rawRefreshToken)) {
            return RefreshTransactionService.RefreshOutcome.unauthorized();
        }
        String tokenHash = refreshTokenFactory.hash(rawRefreshToken);
        Optional<AuthRefreshToken> found = authRefreshTokenRepository.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return RefreshTransactionService.RefreshOutcome.unauthorized();
        }

        UUID sessionId = found.get().getSession().getId();
        if (!csrfTokenService.isValid(sessionId, csrfToken)) {
            return RefreshTransactionService.RefreshOutcome.unauthorized();
        }
        return refreshTransactionService.rotate(sessionId, tokenHash);
    }

    private boolean isUsable(
            AuthSession session, AuthRefreshToken token, LocalDateTime now) {
        return session.getRevokedAt() == null
                && session.getExpiresAt().isAfter(now)
                && token.getConsumedAt() == null
                && token.getRevokedAt() == null
                && token.getExpiresAt().isAfter(now);
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    public record LoginSessionResult(
            LoginResponse response,
            String refreshToken,
            LocalDateTime refreshTokenExpiresAt) {
    }

    public RefreshResponse toResponse(RefreshTransactionService.RefreshOutcome outcome) {
        return new RefreshResponse(
                outcome.accessToken().value(),
                "Bearer",
                outcome.accessToken().expiresInSeconds());
    }

    @Transactional
    public boolean logout(String rawRefreshToken, String csrfToken) {
        if (!refreshTokenFactory.isWellFormed(rawRefreshToken)) {
            return false;
        }
        String tokenHash = refreshTokenFactory.hash(rawRefreshToken);
        Optional<AuthRefreshToken> found = authRefreshTokenRepository.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return false;
        }

        UUID sessionId = found.get().getSession().getId();
        if (!csrfTokenService.isValid(sessionId, csrfToken)) {
            return false;
        }
        if (!logoutTransactionService.revokeSessionFamily(sessionId, tokenHash)) {
            return false;
        }
        authSessionRepository.findById(sessionId).ifPresent(loginAuditService::recordLogout);
        return true;
    }
}
