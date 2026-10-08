package com.lucidia.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {
    List<AuditLogEntry> findByResourceIdOrderByTimestampAsc(UUID resourceId);

    List<AuditLogEntry> findTopByOrderByTimestampDesc();

    /**
     * Anonymises audit log rows for a deleted user: sets actorUserId to a
     * zeroed-out UUID so the row is retained for legal/integrity purposes
     * but no longer linked to personal data.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AuditLogEntry e SET e.actorUserId = :anonId WHERE e.actorUserId = :userId")
    void anonymiseForUser(@Param("userId") UUID userId, @Param("anonId") UUID anonId);
}