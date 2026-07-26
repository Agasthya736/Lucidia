package com.lucidia.backend.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID actorUserId;
    private String action;
    private UUID resourceId;
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
    }

    /** The exact string that gets hashed - must include every field that matters for integrity. */
    public String canonicalContent() {
        return actorUserId + "|" + action + "|" + resourceId + "|" + timestamp + "|" + previousEntryHash;
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