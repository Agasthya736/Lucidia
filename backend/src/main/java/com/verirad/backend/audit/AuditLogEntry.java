package com.verirad.backend.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A single tamper-evident audit log entry.
 *
 * Every access or modification of a CT scan or report writes one of these.
 * Each entry stores the SHA-256 hash of the *previous* entry alongside its
 * own content hash - a lightweight hash chain (not a blockchain; no
 * distributed consensus needed for a single-writer audit log, but the
 * same tamper-evidence property: altering any past entry breaks every
 * hash after it, which is detectable on verification).
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID actorUserId;
    private String action;       // e.g. "VIEW_SCAN", "EDIT_REPORT", "FINALIZE_REPORT"
    private UUID resourceId;     // the scan or report acted upon
    private Instant timestamp;

    @Column(length = 64)
    private String previousEntryHash;

    @Column(length = 64)
    private String entryHash;

    protected AuditLogEntry() {
        // JPA
    }

    public AuditLogEntry(UUID actorUserId, String action, UUID resourceId, String previousEntryHash) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceId = resourceId;
        this.timestamp = Instant.now();
        this.previousEntryHash = previousEntryHash;
        // entryHash computed by AuditLogService at write time, over all fields above
    }

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public UUID getResourceId() { return resourceId; }
    public Instant getTimestamp() { return timestamp; }
    public String getPreviousEntryHash() { return previousEntryHash; }
    public String getEntryHash() { return entryHash; }
    public void setEntryHash(String entryHash) { this.entryHash = entryHash; }
}
