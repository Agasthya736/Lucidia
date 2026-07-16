package com.verirad.backend.orchestrator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shared "blackboard" state object passed through the agent pipeline.
 * Every agent reads from and writes to this object; the full history of
 * a job is reconstructable from its final state, which is what gets
 * persisted for audit and evaluation purposes.
 *
 * Pipeline stages, in order:
 *   RECEIVED -> VISION -> ARBITRATION -> EXPLAINABILITY -> WRITING -> VERIFIED -> FINALIZED
 */
public class PipelineState {

    public enum Stage {
        RECEIVED, VISION, ARBITRATION, EXPLAINABILITY, WRITING, VERIFIED, FINALIZED, NEEDS_REVIEW, FAILED
    }

    private final UUID jobId;
    private final UUID submittedByUserId;
    private final Instant createdAt;
    private Stage stage;

    // Vision agent outputs - two independent reads, per the dual-agent consensus design
    private String visionFindingsA;
    private String visionFindingsB;

    // Arbiter output
    private boolean visionAgentsAgree;
    private String arbitrationNotes;

    // Explainability agent output - reference to a stored heatmap overlay image
    private String heatmapImageRef;

    // Structured report sections
    private final Map<String, String> reportSections = new HashMap<>();

    // Verifier flags - unsupported claims, low-confidence statements, etc.
    private final List<String> flags = new ArrayList<>();

    public PipelineState(UUID jobId, UUID submittedByUserId) {
        this.jobId = jobId;
        this.submittedByUserId = submittedByUserId;
        this.createdAt = Instant.now();
        this.stage = Stage.RECEIVED;
    }

    public UUID getJobId() { return jobId; }
    public UUID getSubmittedByUserId() { return submittedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }

    public String getVisionFindingsA() { return visionFindingsA; }
    public void setVisionFindingsA(String v) { this.visionFindingsA = v; }
    public String getVisionFindingsB() { return visionFindingsB; }
    public void setVisionFindingsB(String v) { this.visionFindingsB = v; }

    public boolean isVisionAgentsAgree() { return visionAgentsAgree; }
    public void setVisionAgentsAgree(boolean agree) { this.visionAgentsAgree = agree; }
    public String getArbitrationNotes() { return arbitrationNotes; }
    public void setArbitrationNotes(String notes) { this.arbitrationNotes = notes; }

    public String getHeatmapImageRef() { return heatmapImageRef; }
    public void setHeatmapImageRef(String ref) { this.heatmapImageRef = ref; }

    public Map<String, String> getReportSections() { return reportSections; }
    public List<String> getFlags() { return flags; }
}
