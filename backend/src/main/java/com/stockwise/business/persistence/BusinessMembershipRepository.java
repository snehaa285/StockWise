package com.stockwise.business.persistence;

import com.stockwise.business.domain.BusinessMembership;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BusinessMembershipRepository extends JpaRepository<BusinessMembership, UUID> {
    Optional<BusinessMembership> findByBusiness_IdAndUser_IdAndRemovedAtIsNull(
            UUID businessId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from BusinessMembership membership "
            + "where membership.business.id = :businessId and membership.user.id = :userId")
    Optional<BusinessMembership> findByBusinessAndUserForUpdate(
            @Param("businessId") UUID businessId, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"business"})
    List<BusinessMembership> findAllByUser_IdAndRemovedAtIsNullOrderByCreatedAtAsc(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    List<BusinessMembership> findAllByBusiness_IdAndRemovedAtIsNullOrderByCreatedAtAsc(
            UUID businessId);

    @Modifying
    @Transactional
    @Query("delete from BusinessMembership membership where membership.business.id = :businessId")
    void deleteAllByBusinessId(@Param("businessId") UUID businessId);

    @Modifying
    @Transactional
    @Query("delete from BusinessMembership membership where membership.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
