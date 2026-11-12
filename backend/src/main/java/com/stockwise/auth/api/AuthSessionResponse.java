package com.stockwise.auth.api;

import com.stockwise.auth.domain.AuthSession;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuthSessionResponse(
        UUID id,
        LocalDateTime createdAt,
        LocalDateTime lastActivityAt,
        LocalDateTime expiresAt,
        String authenticationMethod,
        String userAgent,
        boolean current) {
    public static AuthSessionResponse from(AuthSession session, UUID currentSessionId) {
        return new AuthSessionResponse(
                session.getId(),
                session.getCreatedAt(),
                session.getLastRotatedAt() == null
                        ? session.getCreatedAt() : session.getLastRotatedAt(),
                session.getExpiresAt(),
                session.getAuthenticationMethod().name(),
                session.getUserAgent(),
                session.getId().equals(currentSessionId));
    }
}
