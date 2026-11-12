package com.stockwise.excelsync.persistence;

import com.stockwise.excelsync.domain.MicrosoftOAuthState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface MicrosoftOAuthStateRepository extends JpaRepository<MicrosoftOAuthState, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from MicrosoftOAuthState state where state.stateHash = :stateHash")
    Optional<MicrosoftOAuthState> findByStateHashForUpdate(
            @Param("stateHash") String stateHash);

    @Modifying
    @Transactional
    void deleteAllByExpiresAtBefore(java.time.LocalDateTime expiresAt);
}
