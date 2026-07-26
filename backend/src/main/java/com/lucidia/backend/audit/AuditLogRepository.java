package com.lucidia.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {
    List<AuditLogEntry> findByResourceIdOrderByTimestampAsc(UUID resourceId);
    List<AuditLogEntry> findTopByOrderByTimestampDesc();
}