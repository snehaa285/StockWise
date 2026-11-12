package com.stockwise.auth.persistence;

import com.stockwise.auth.domain.AuthLoginEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AuthLoginEventRepository extends JpaRepository<AuthLoginEvent, UUID> {
    List<AuthLoginEvent> findTop100ByUser_IdOrderByOccurredAtDesc(UUID userId);

    @Modifying
    @Transactional
    @Query("delete from AuthLoginEvent event where event.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("delete from AuthLoginEvent event where event.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
