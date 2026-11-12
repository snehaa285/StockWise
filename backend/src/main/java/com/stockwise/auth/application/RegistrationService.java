package com.stockwise.auth.application;

import com.stockwise.auth.api.RegisterRequest;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.persistence.AppUserRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

@Service
@Profile("mysql")
public class RegistrationService {
    private final AppUserRepository appUserRepository;
    private final AppUserWriter appUserWriter;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            AppUserRepository appUserRepository,
            AppUserWriter appUserWriter,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.appUserWriter = appUserWriter;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new PasswordTooLongException();
        }

        String normalizedEmail = EmailNormalizer.normalize(request.email());
        if (appUserRepository.existsByEmailNormalized(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        AppUser user = AppUser.register(
                request.displayName(),
                normalizedEmail,
                passwordEncoder.encode(request.password()));

        try {
            return appUserWriter.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (appUserRepository.existsByEmailNormalized(normalizedEmail)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
    }
}
