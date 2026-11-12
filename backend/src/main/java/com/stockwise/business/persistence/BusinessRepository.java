package com.stockwise.business.persistence;

import com.stockwise.business.domain.Business;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select business from Business business where business.id = :id")
    Optional<Business> findByIdForUpdate(@Param("id") UUID id);
}
