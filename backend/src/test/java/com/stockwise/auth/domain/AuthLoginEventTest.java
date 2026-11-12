package com.stockwise.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuthLoginEventTest {
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.parse("2026-11-12T12:00:00");

    @Test
    void createsLoginFailureWithoutUserOrSession() {
        AuthLoginEvent event = AuthLoginEvent.create(
                null,
                null,
                AuthEventType.LOGIN_FAILURE,
                AuthenticationMethod.EMAIL_PASSWORD,
                OCCURRED_AT,
                null);

        assertThat(event.getUser()).isNull();
        assertThat(event.getSession()).isNull();
        assertThat(event.getEventType()).isEqualTo(AuthEventType.LOGIN_FAILURE);
    }

    @Test
    void createsEventForTheOwningUserAndSession() {
        AppUser user = user("owner@example.com");
        AuthSession session = session(user);

        AuthLoginEvent event = AuthLoginEvent.create(
                user,
                session,
                AuthEventType.LOGIN_SUCCESS,
                AuthenticationMethod.EMAIL_PASSWORD,
                OCCURRED_AT,
                "Test Agent");

        assertThat(event.getUser().getId()).isEqualTo(user.getId());
        assertThat(event.getSession().getId()).isEqualTo(session.getId());
    }

    @Test
    void rejectsNonFailureEventWithoutSession() {
        assertThatIllegalArgumentException().isThrownBy(() -> AuthLoginEvent.create(
                user("owner@example.com"),
                null,
                AuthEventType.LOGIN_SUCCESS,
                AuthenticationMethod.EMAIL_PASSWORD,
                OCCURRED_AT,
                null));
    }

    @Test
    void rejectsSessionBelongingToAnotherUser() {
        AppUser user = user("event-user@example.com");
        AuthSession otherUsersSession = session(user("session-owner@example.com"));

        assertThatIllegalArgumentException().isThrownBy(() -> AuthLoginEvent.create(
                user,
                otherUsersSession,
                AuthEventType.LOGIN_SUCCESS,
                AuthenticationMethod.EMAIL_PASSWORD,
                OCCURRED_AT,
                null));
    }

    @Test
    void rejectsSessionWhenEventUserIsMissing() {
        assertThatIllegalArgumentException().isThrownBy(() -> AuthLoginEvent.create(
                null,
                session(user("session-owner@example.com")),
                AuthEventType.LOGIN_SUCCESS,
                AuthenticationMethod.EMAIL_PASSWORD,
                OCCURRED_AT,
                null));
    }

    @Test
    void rejectsMissingEventTypeOrAuthenticationMethod() {
        AppUser user = user("owner@example.com");
        AuthSession session = session(user);

        assertThatIllegalArgumentException().isThrownBy(() -> AuthLoginEvent.create(
                user,
                session,
                null,
                AuthenticationMethod.EMAIL_PASSWORD,
                OCCURRED_AT,
                null));
        assertThatIllegalArgumentException().isThrownBy(() -> AuthLoginEvent.create(
                user,
                session,
                AuthEventType.LOGIN_SUCCESS,
                null,
                OCCURRED_AT,
                null));
    }

    private static AppUser user(String email) {
        return AppUser.register("User", email, "password-hash");
    }

    private static AuthSession session(AppUser user) {
        return AuthSession.create(
                user,
                OCCURRED_AT,
                AuthenticationMethod.EMAIL_PASSWORD,
                null);
    }
}
