package com.lucidia.backend.scan;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scans")
public class Scan {

    public enum Status {
        RECEIVED, PROCESSING, COMPLETED, FAILED, FINALIZED
    }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.RECEIVED;

    @Column(name = "image_filename")
    private String imageFilename;

    @Column(name = "vision_a_json", columnDefinition = "TEXT")
    private String visionAJson;

    @Column(name = "vision_b_json", columnDefinition = "TEXT")
    private String visionBJson;

    @Column(name = "arbitration_json", columnDefinition = "TEXT")
    private String arbitrationJson;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "verification_json", columnDefinition = "TEXT")
    private String verificationJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected Scan() {
        // JPA
    }

    public Scan(UUID userId, String imageFilename) {
        this.userId = userId;
        this.imageFilename = imageFilename;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getImageFilename() { return imageFilename; }

    public String getVisionAJson() { return visionAJson; }
    public void setVisionAJson(String v) { this.visionAJson = v; }
    public String getVisionBJson() { return visionBJson; }
    public void setVisionBJson(String v) { this.visionBJson = v; }
    public String getArbitrationJson() { return arbitrationJson; }
    public void setArbitrationJson(String v) { this.arbitrationJson = v; }
    public String getReportJson() { return reportJson; }
    public void setReportJson(String v) { this.reportJson = v; }
    public String getVerificationJson() { return verificationJson; }
    public void setVerificationJson(String v) { this.verificationJson = v; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant v) { this.finalizedAt = v; }
}