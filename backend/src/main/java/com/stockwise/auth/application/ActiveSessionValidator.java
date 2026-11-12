package com.stockwise.auth.application;

import com.stockwise.auth.persistence.AuthSessionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class ActiveSessionValidator {
    private final AuthSessionRepository authSessionRepository;

    public ActiveSessionValidator(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, UUID userId) {
        return authSessionRepository.existsByIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(
                sessionId, userId, LocalDateTime.now(ZoneOffset.UTC));
    }
}
