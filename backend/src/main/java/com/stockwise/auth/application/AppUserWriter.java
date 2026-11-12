package com.stockwise.auth.application;

import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.persistence.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class AppUserWriter {
    private final AppUserRepository appUserRepository;

    public AppUserWriter(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppUser saveAndFlush(AppUser user) {
        return appUserRepository.saveAndFlush(user);
    }
}
