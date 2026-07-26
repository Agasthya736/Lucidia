package com.lucidia.backend.audit;

import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditLogEntry record(UUID actorUserId, String action, UUID resourceId) {
        String previousHash = getLastEntryHash();
        AuditLogEntry entry = new AuditLogEntry(actorUserId, action, resourceId, previousHash);
        entry.setEntryHash(sha256(entry.canonicalContent()));
        return auditLogRepository.save(entry);
    }

    public List<AuditLogEntry> getTrailFor(UUID resourceId) {
        return auditLogRepository.findByResourceIdOrderByTimestampAsc(resourceId);
    }

    /** Walks the full chain for a resource and confirms no entry was tampered with. */
    public boolean verifyChainIntegrity(UUID resourceId) {
        List<AuditLogEntry> entries = getTrailFor(resourceId);
        String expectedPrevious = null;
        for (AuditLogEntry entry : entries) {
            if (!java.util.Objects.equals(entry.getPreviousEntryHash(), expectedPrevious)) {
                return false;
            }
            String recomputed = sha256(entry.canonicalContent());
            if (!recomputed.equals(entry.getEntryHash())) {
                return false;
            }
            expectedPrevious = entry.getEntryHash();
        }
        return true;
    }

    private String getLastEntryHash() {
        List<AuditLogEntry> last = auditLogRepository.findTopByOrderByTimestampDesc();
        return last.isEmpty() ? null : last.get(0).getEntryHash();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}