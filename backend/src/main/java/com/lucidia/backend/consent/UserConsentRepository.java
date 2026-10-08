package com.lucidia.backend.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    Optional<UserConsent> findByUserIdAndVersion(UUID userId, String version);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserConsent c WHERE c.userId = :userId")
    void deleteByUserId(UUID userId);
}
