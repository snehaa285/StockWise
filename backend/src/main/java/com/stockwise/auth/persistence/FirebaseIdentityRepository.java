package com.stockwise.auth.persistence;

import com.stockwise.auth.domain.FirebaseIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirebaseIdentityRepository extends JpaRepository<FirebaseIdentity, UUID> {
    Optional<FirebaseIdentity> findByFirebaseUid(String firebaseUid);

    Optional<FirebaseIdentity> findByUser_Id(UUID userId);
}
