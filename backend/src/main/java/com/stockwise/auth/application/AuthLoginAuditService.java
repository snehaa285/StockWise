package com.stockwise.auth.application;

import com.stockwise.auth.domain.AuthEventType;
import com.stockwise.auth.domain.AuthLoginEvent;
import com.stockwise.auth.domain.AuthSession;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.domain.AuthenticationMethod;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class AuthLoginAuditService {
    private static final int USER_AGENT_MAX_LENGTH = 512;
    private static final int RETENTION_DAYS = 365;

    private final AuthLoginEventRepository eventRepository;

    public AuthLoginAuditService(AuthLoginEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(
            AppUser user, AuthenticationMethod method, String userAgent) {
        eventRepository.save(event(
                user, null, AuthEventType.LOGIN_FAILURE, method, userAgent));
    }

    @Transactional
    public void recordLoginSuccess(AuthSession session) {
        eventRepository.save(event(
                session.getUser(),
                session,
                AuthEventType.LOGIN_SUCCESS,
                session.getAuthenticationMethod(),
                session.getUserAgent()));
    }

    @Transactional
    public void recordLogout(AuthSession session) {
        eventRepository.save(event(
                session,
                AuthEventType.LOGOUT));
    }

    @Transactional
    public void recordSessionRevoked(AuthSession session) {
        eventRepository.save(event(
                session,
                AuthEventType.SESSION_REVOKED));
    }

    @Transactional(readOnly = true)
    public List<AuthLoginEvent> recentHistory(UUID userId) {
        return eventRepository.findTop100ByUser_IdOrderByOccurredAtDesc(userId);
    }

    @Scheduled(fixedDelayString = "${stockwise.auth.audit.cleanup-interval-ms:86400000}")
    @Transactional
    public void removeExpiredHistory() {
        eventRepository.deleteOlderThan(
                LocalDateTime.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS));
    }

    private AuthLoginEvent event(
            AuthSession session,
            AuthEventType eventType) {
        return event(
                session.getUser(),
                session,
                eventType,
                session.getAuthenticationMethod(),
                session.getUserAgent());
    }

    private AuthLoginEvent event(
            AppUser user,
            AuthSession session,
            AuthEventType eventType,
            AuthenticationMethod method,
            String userAgent) {
        return AuthLoginEvent.create(
                user,
                session,
                eventType,
                method,
                LocalDateTime.now(ZoneOffset.UTC),
                sanitizeUserAgent(userAgent));
    }

    public String sanitizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String normalized = userAgent.replaceAll("[\\p{Cntrl}]", " ").trim();
        return normalized.length() <= USER_AGENT_MAX_LENGTH
                ? normalized : normalized.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
