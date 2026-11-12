package com.stockwise.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.domain.AuthEventType;
import com.stockwise.auth.domain.AuthLoginEvent;
import com.stockwise.auth.domain.AuthSession;
import com.stockwise.auth.domain.AuthenticationMethod;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthLoginAuditServiceTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-11-12T12:00:00");

    private final AuthLoginEventRepository repository = mock(AuthLoginEventRepository.class);
    private final AuthLoginAuditService service = new AuthLoginAuditService(repository);

    @Test
    void recordsAnonymousLoginFailureWithoutSession() {
        service.recordLoginFailure(null, AuthenticationMethod.EMAIL_PASSWORD, "Agent");

        AuthLoginEvent event = savedEvent();
        assertThat(event.getUser()).isNull();
        assertThat(event.getSession()).isNull();
        assertThat(event.getEventType()).isEqualTo(AuthEventType.LOGIN_FAILURE);
    }

    @Test
    void recordsSessionEventsWithTheSessionOwner() {
        AppUser user = AppUser.register("User", "owner@example.com", "password-hash");
        AuthSession session = AuthSession.create(
                user, CREATED_AT, AuthenticationMethod.EMAIL_PASSWORD, "Agent");

        service.recordLoginSuccess(session);
        assertSavedEvent(session, AuthEventType.LOGIN_SUCCESS);

        service.recordLogout(session);
        assertSavedEvent(session, AuthEventType.LOGOUT);

        service.recordSessionRevoked(session);
        assertSavedEvent(session, AuthEventType.SESSION_REVOKED);
    }

    private void assertSavedEvent(AuthSession session, AuthEventType type) {
        AuthLoginEvent event = savedEvent();
        assertThat(event.getUser().getId()).isEqualTo(session.getUser().getId());
        assertThat(event.getSession().getId()).isEqualTo(session.getId());
        assertThat(event.getEventType()).isEqualTo(type);
    }

    private AuthLoginEvent savedEvent() {
        ArgumentCaptor<AuthLoginEvent> captor = ArgumentCaptor.forClass(AuthLoginEvent.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
