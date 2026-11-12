package com.stockwise.auth.persistence;

import com.stockwise.auth.domain.AuthSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :id")
    Optional<AuthSession> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID id, UUID userId, LocalDateTime now);

    List<AuthSession> findAllByUser_Id(UUID userId);

    List<AuthSession> findAllByUser_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session "
            + "where session.user.id = :userId "
            + "and session.revokedAt is null and session.expiresAt > :now "
            + "order by session.id")
    List<AuthSession> findActiveByUserForUpdate(
            @Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("delete from AuthSession session where session.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
