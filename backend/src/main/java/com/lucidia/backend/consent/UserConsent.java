package com.lucidia.backend.consent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Records whether a user has accepted the informed-consent/disclaimer screen.
 * One row per (user, version). Must exist with accepted=true before the user
 * can submit a scan (enforced in ScanController).
 */
@Entity
@Table(name = "user_consents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "version"}))
public class UserConsent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean accepted;

    @Column(nullable = false, length = 20)
    private String version = "1.0";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserConsent() {}

    public UserConsent(UUID userId, boolean accepted, String version) {
        this.userId = userId;
        this.accepted = accepted;
        this.version = version != null ? version : "1.0";
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public boolean isAccepted() { return accepted; }
    public String getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
