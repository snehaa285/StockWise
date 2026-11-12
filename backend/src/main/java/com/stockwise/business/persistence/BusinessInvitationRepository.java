package com.stockwise.business.persistence;

import com.stockwise.business.domain.BusinessInvitation;
import com.stockwise.business.domain.BusinessInvitationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BusinessInvitationRepository
        extends JpaRepository<BusinessInvitation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from BusinessInvitation invitation "
            + "where invitation.tokenHash = :tokenHash")
    Optional<BusinessInvitation> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from BusinessInvitation invitation "
            + "where invitation.id = :invitationId and invitation.business.id = :businessId")
    Optional<BusinessInvitation> findByBusinessAndIdForUpdate(
            @Param("businessId") UUID businessId,
            @Param("invitationId") UUID invitationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BusinessInvitation> findByBusiness_IdAndEmailNormalizedAndStatus(
            UUID businessId, String emailNormalized, BusinessInvitationStatus status);

    List<BusinessInvitation> findAllByBusiness_IdOrderByCreatedAtDesc(UUID businessId);

    @Modifying
    @Transactional
    @Query("delete from BusinessInvitation invitation where invitation.business.id = :businessId")
    void deleteAllByBusinessId(@Param("businessId") UUID businessId);
}
