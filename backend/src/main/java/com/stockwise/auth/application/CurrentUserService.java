package com.stockwise.auth.application;

import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.persistence.AppUserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class CurrentUserService {
    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findById(UUID userId) {
        return appUserRepository.findById(userId);
    }
}
