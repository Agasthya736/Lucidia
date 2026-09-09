package com.lucidia.backend.orchestrator;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.verifier.VerificationResult;
import com.lucidia.backend.agents.verifier.VerifierAgent;
import com.lucidia.backend.synthesis.GroundedReport;
import com.lucidia.backend.synthesis.ReportSynthesisService;
import com.lucidia.backend.triage.AggregatedFindings;
import com.lucidia.backend.triage.SliceInput;
import com.lucidia.backend.triage.TriageDetector;

/**
 * Redesigned Detector-Grounded Triage Pipeline Orchestrator.
 * Replaces the old dual-vision-agent / Arbiter design with:
 * 1. Triage Detector running per slice on pixels, outputting structured findings & confidence.
 * 2. Cost-control branching: High-confidence clean scans bypass LLM synthesis completely.
 * 3. Grounded Synthesis: Abnormal / low-confidence scans produce structured reports from detector evidence.
 * 4. Grounding Verifier: Verifies report claims strictly against detector evidence.
 */
@Service
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final TriageDetector triageDetector;
    private final ReportSynthesisService reportSynthesisService;
    private final VerifierAgent verifierAgent;
    private final double confidenceThreshold;

    public PipelineOrchestrator(
            TriageDetector triageDetector,
            ReportSynthesisService reportSynthesisService,
            VerifierAgent verifierAgent,
            @Value("${lucidia.triage.confidence-threshold:0.85}") double confidenceThreshold) {
        this.triageDetector = triageDetector;
        this.reportSynthesisService = reportSynthesisService;
        this.verifierAgent = verifierAgent;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Overload for single-image backwards compatibility.
     */
    public PipelineResult run(byte[] imageBytes, String mimeType) {
        return run(List.of(new SliceInput(0, "slice_001.jpg", imageBytes, mimeType)), null);
    }

    /**
     * Primary entry point for multi-slice CT study pipeline execution.
     */
    public PipelineResult run(List<SliceInput> slices, String customApiKey) {
        List<String> warnings = new ArrayList<>();

        if (slices == null || slices.isEmpty()) {
            throw new PipelineFailedException("CT study contains no slices to analyze.");
        }

        log.info("Starting triage detector on CT series of {} slices using {}",
                slices.size(), triageDetector.getDetectorName());

        // 1. Run per-slice triage detector and aggregate evidence
        AggregatedFindings triage;
        try {
            triage = triageDetector.analyzeSeries(slices);
        } catch (Exception e) {
            log.error("Triage detector execution failed: {}", e.getMessage(), e);
            throw new PipelineFailedException("Triage detector failed: " + e.getMessage());
        }

        log.info("Triage complete: status={}, abnormalSlices={}, avgConfidence={}",
                triage.overallStatus(), triage.abnormalSlicesCount(), triage.overallConfidence());

        // 2. Cost-control Branching
        boolean isClean = triage.isHighConfidenceClean(confidenceThreshold);
        boolean isEscalated = !isClean;

        GroundedReport report;
        if (isClean) {
            log.info("Study is HIGH-CONFIDENCE CLEAN ({}) -> Generating instant auto-summary, skipping LLM.",
                    triage.overallConfidence());
            report = GroundedReport.createCleanAutoSummary(triage);
        } else {
            log.info("Study requires grounded synthesis (Status: {}, Abnormal Slices: {}) -> Escalating to synthesis provider.",
                    triage.overallStatus(), triage.abnormalSlicesCount());
            try {
                report = reportSynthesisService.synthesizeReport(triage, customApiKey);
            } catch (Exception e) {
                log.error("Grounded synthesis failed: {}. Using fallback grounded report.", e.getMessage());
                warnings.add("Grounded synthesis service encountered an issue; generated using deterministic engine: " + e.getMessage());
                report = GroundedReport.createCleanAutoSummary(triage);
            }
        }

        // 3. Grounding Verification
        VerificationResult verification;
        try {
            verification = verifierAgent.verify(report, triage);
        } catch (Exception e) {
            log.warn("Verifier check encountered an error: {}", e.getMessage());
            verification = VerificationResult.unavailable("Verification check error: " + e.getMessage());
            warnings.add("Verification non-fatal check error: " + e.getMessage());
        }

        return new PipelineResult(triage, report, verification, isEscalated, warnings);
    }
}