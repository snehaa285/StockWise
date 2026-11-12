package com.stockwise.auth.api;

import com.stockwise.auth.domain.AuthEventType;
import com.stockwise.auth.domain.AuthLoginEvent;
import com.stockwise.auth.domain.AuthenticationMethod;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuthLoginHistoryResponse(
        UUID id,
        AuthEventType eventType,
        AuthenticationMethod authenticationMethod,
        boolean successful,
        UUID sessionId,
        LocalDateTime occurredAt,
        String userAgent) {
    public static AuthLoginHistoryResponse from(AuthLoginEvent event) {
        return new AuthLoginHistoryResponse(
                event.getId(),
                event.getEventType(),
                event.getAuthenticationMethod(),
                event.getEventType() != AuthEventType.LOGIN_FAILURE,
                event.getSession() == null ? null : event.getSession().getId(),
                event.getOccurredAt(),
                event.getUserAgent());
    }
}
