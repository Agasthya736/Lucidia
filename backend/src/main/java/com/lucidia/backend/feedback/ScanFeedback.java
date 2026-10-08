package com.lucidia.backend.feedback;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores "was this result wrong or unhelpful?" feedback from the user
 * after viewing a scan report.
 */
@Entity
@Table(name = "scan_feedback")
public class ScanFeedback {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 1 = very unhelpful … 5 = very helpful */
    @Column(nullable = false)
    private short rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ScanFeedback() {}

    public ScanFeedback(UUID scanId, UUID userId, short rating, String comment) {
        this.scanId = scanId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
    }

    public UUID getId()        { return id; }
    public UUID getScanId()    { return scanId; }
    public UUID getUserId()    { return userId; }
    public short getRating()   { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
