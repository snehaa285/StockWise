package com.stockwise.auth.persistence;

import com.stockwise.auth.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    boolean existsByEmailNormalized(String emailNormalized);

    Optional<AppUser> findByEmailNormalized(String emailNormalized);

    @Transactional
    void deleteByEmailNormalized(String emailNormalized);
}
