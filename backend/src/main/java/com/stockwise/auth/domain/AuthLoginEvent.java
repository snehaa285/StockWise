package com.stockwise.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_login_event")
public class AuthLoginEvent {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private AuthSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private AuthEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_method", nullable = false, length = 24)
    private AuthenticationMethod authenticationMethod;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected AuthLoginEvent() {
    }

    private AuthLoginEvent(
            UUID id,
            AppUser user,
            AuthSession session,
            AuthEventType eventType,
            AuthenticationMethod authenticationMethod,
            LocalDateTime occurredAt,
            String userAgent) {
        this.id = id;
        this.user = user;
        this.session = session;
        this.eventType = eventType;
        this.authenticationMethod = authenticationMethod;
        this.occurredAt = occurredAt;
        this.userAgent = userAgent;
    }

    public static AuthLoginEvent create(
            AppUser user,
            AuthSession session,
            AuthEventType eventType,
            AuthenticationMethod authenticationMethod,
            LocalDateTime occurredAt,
            String userAgent) {
        return new AuthLoginEvent(
                UUID.randomUUID(),
                user,
                session,
                eventType,
                authenticationMethod,
                occurredAt,
                userAgent);
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public AuthSession getSession() {
        return session;
    }

    public AuthEventType getEventType() {
        return eventType;
    }

    public AuthenticationMethod getAuthenticationMethod() {
        return authenticationMethod;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
