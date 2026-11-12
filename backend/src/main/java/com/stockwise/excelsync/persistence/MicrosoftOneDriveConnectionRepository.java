package com.stockwise.excelsync.persistence;

import com.stockwise.excelsync.domain.MicrosoftConnectionStatus;
import com.stockwise.excelsync.domain.MicrosoftOneDriveConnection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MicrosoftOneDriveConnectionRepository
        extends JpaRepository<MicrosoftOneDriveConnection, UUID> {
    boolean existsByBusinessIdAndAuthorizationStatus(
            UUID businessId, MicrosoftConnectionStatus authorizationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from MicrosoftOneDriveConnection connection "
            + "where connection.businessId = :businessId")
    Optional<MicrosoftOneDriveConnection> findByBusinessIdForUpdate(
            @Param("businessId") UUID businessId);
}
