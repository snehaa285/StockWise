package com.stockwise.auth.application;

import com.stockwise.auth.api.LoginRequest;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.domain.AuthenticationMethod;
import com.stockwise.auth.persistence.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("mysql")
public class LoginService {
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final AuthLoginAuditService loginAuditService;
    private final String timingHash;

    public LoginService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService,
            AuthLoginAuditService loginAuditService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.loginAuditService = loginAuditService;
        this.timingHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AuthSessionService.LoginSessionResult login(
            LoginRequest request, String userAgent) {
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        AppUser user = appUserRepository.findByEmailNormalized(normalizedEmail).orElse(null);
        boolean passwordWithinBcryptLimit =
                request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        String hashToCheck = user == null || user.getPasswordHash() == null
                ? timingHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || user.getPasswordHash() == null
                || !passwordWithinBcryptLimit || !passwordMatches) {
            loginAuditService.recordLoginFailure(
                    user, AuthenticationMethod.EMAIL_PASSWORD, userAgent);
            throw new InvalidCredentialsException();
        }

        return authSessionService.createSession(
                user, AuthenticationMethod.EMAIL_PASSWORD, userAgent);
    }
}
