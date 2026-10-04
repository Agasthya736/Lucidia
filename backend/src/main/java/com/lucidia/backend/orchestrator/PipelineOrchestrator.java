package com.lucidia.backend.orchestrator;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.verifier.VerificationResult;
import com.lucidia.backend.agents.verifier.VerifierAgent;
import com.lucidia.backend.responsibleai.ResponsibleAiGuardrailService;
import com.lucidia.backend.synthesis.GroundedReport;
import com.lucidia.backend.synthesis.ReportSynthesisService;
import com.lucidia.backend.triage.AggregatedFindings;
import com.lucidia.backend.triage.ExternalPhotoTriageDetector;
import com.lucidia.backend.triage.SliceInput;
import com.lucidia.backend.triage.TriageDetector;

/**
 * Responsible AI Multi-Modality Pipeline Orchestrator.
 * Supports both CT series and External Clinical Photography.
 * 1. Responsible AI Guardrail Screening (rejects non-clinical images, memes, corrupt data).
 * 2. Pluggable Triage Detector per modality (CT vs External Clinical Photograph).
 * 3. Cost-control branching: High-confidence clean scans generate instant auto-summaries.
 * 4. Grounded Synthesis: Generates clear, concise clinical findings and actionable next steps.
 * 5. Grounding Verifier: Verifies report claims strictly against detector evidence.
 */
@Service
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final TriageDetector triageDetector;
    private final ExternalPhotoTriageDetector externalPhotoDetector;
    private final ResponsibleAiGuardrailService responsibleAiService;
    private final ReportSynthesisService reportSynthesisService;
    private final VerifierAgent verifierAgent;
    private final double confidenceThreshold;

    public PipelineOrchestrator(
            TriageDetector triageDetector,
            ExternalPhotoTriageDetector externalPhotoDetector,
            ResponsibleAiGuardrailService responsibleAiService,
            ReportSynthesisService reportSynthesisService,
            VerifierAgent verifierAgent,
            @Value("${lucidia.triage.confidence-threshold:0.85}") double confidenceThreshold) {
        this.triageDetector = triageDetector;
        this.externalPhotoDetector = externalPhotoDetector;
        this.responsibleAiService = responsibleAiService;
        this.reportSynthesisService = reportSynthesisService;
        this.verifierAgent = verifierAgent;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Overload for single-image backwards compatibility.
     */
    public PipelineResult run(byte[] imageBytes, String mimeType) {
        return run(List.of(new SliceInput(0, "slice_001.jpg", imageBytes, mimeType)), "CT_SERIES", null, null);
    }

    /**
     * Overload for backwards compatibility without explicit modality.
     */
    public PipelineResult run(List<SliceInput> slices, String customApiKey) {
        return run(slices, "CT_SERIES", null, customApiKey);
    }

    /**
     * Primary entry point for multi-modality clinical study execution.
     */
    public PipelineResult run(List<SliceInput> slices, String modality, String clinicalNotes, String customApiKey) {
        List<String> warnings = new ArrayList<>();

        if (slices == null || slices.isEmpty()) {
            throw new PipelineFailedException("Study contains no images to analyze.");
        }

        String effectiveModality = (modality != null && !modality.isBlank()) ? modality.toUpperCase() : "CT_SERIES";
        boolean isExternalPhoto = "EXTERNAL_PHOTO".equalsIgnoreCase(effectiveModality);

        // 1. Responsible AI Guardrail Screening
        responsibleAiService.validateUpload(slices, effectiveModality);

        log.info("Starting triage detector on {} series of {} images (modality: {})",
                isExternalPhoto ? "External Clinical Photo" : "CT Study",
                slices.size(),
                effectiveModality);

        // 2. Run per-slice triage detector and aggregate evidence
        AggregatedFindings triage;
        try {
            if (isExternalPhoto) {
                triage = externalPhotoDetector.analyzePhotos(slices);
            } else {
                triage = triageDetector.analyzeSeries(slices);
            }
        } catch (Exception e) {
            log.error("Triage detector execution failed: {}", e.getMessage(), e);
            throw new PipelineFailedException("Triage detector failed: " + e.getMessage());
        }

        log.info("Triage complete: status={}, abnormalSlices={}, avgConfidence={}",
                triage.overallStatus(), triage.abnormalSlicesCount(), triage.overallConfidence());

        // 3. Cost-control Branching
        boolean isClean = triage.isHighConfidenceClean(confidenceThreshold);
        boolean isEscalated = !isClean;

        GroundedReport report;
        if (isClean) {
            log.info("Study is HIGH-CONFIDENCE CLEAN ({}) -> Generating instant auto-summary, skipping LLM.",
                    triage.overallConfidence());
            report = isExternalPhoto
                    ? GroundedReport.createCleanExternalPhotoSummary(triage)
                    : GroundedReport.createCleanAutoSummary(triage);
        } else {
            log.info("Study requires grounded synthesis (Status: {}, Abnormal Images: {}) -> Escalating to synthesis provider.",
                    triage.overallStatus(), triage.abnormalSlicesCount());
            try {
                report = reportSynthesisService.synthesizeReport(triage, customApiKey);
            } catch (Exception e) {
                log.error("Grounded synthesis failed: {}. Using fallback grounded report.", e.getMessage());
                warnings.add("Grounded synthesis service encountered an issue; generated using deterministic engine: " + e.getMessage());
                report = isExternalPhoto
                        ? GroundedReport.createCleanExternalPhotoSummary(triage)
                        : GroundedReport.createCleanAutoSummary(triage);
            }
        }

        // 4. Grounding Verification
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