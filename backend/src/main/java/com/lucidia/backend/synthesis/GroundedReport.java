package com.lucidia.backend.synthesis;

import java.util.List;
import com.lucidia.backend.triage.AggregatedFindings;

public record GroundedReport(
        List<RegionalFinding> clinicalFindings,
        String impression,
        String severity, // ROUTINE, FOLLOW_UP_RECOMMENDED, URGENT
        double detectorConfidence,
        String recommendations,
        String generatedBy,
        boolean isAutoSummary,
        String executiveSummary,
        String patientFriendlySummary,
        String responsibleAiNotice
) {
    public GroundedReport(
            List<RegionalFinding> clinicalFindings,
            String impression,
            String severity,
            double detectorConfidence,
            String recommendations,
            String generatedBy,
            boolean isAutoSummary) {
        this(
                clinicalFindings,
                impression,
                severity,
                detectorConfidence,
                recommendations,
                generatedBy,
                isAutoSummary,
                impression,
                impression,
                "CLINICAL DECISION SUPPORT NOTICE: Lucidia provides AI-assisted documentation. Clinician review is mandatory."
        );
    }

    public static GroundedReport createCleanAutoSummary(AggregatedFindings findings) {
        List<RegionalFinding> regions = List.of(
                new RegionalFinding("Right Lung", "NORMAL", "Clear parenchyma without focal nodule, consolidation, or suspicious opacity.", List.of()),
                new RegionalFinding("Left Lung", "NORMAL", "Clear parenchyma without infiltrates, masses, or volume loss.", List.of()),
                new RegionalFinding("Mediastinum & Hila", "NORMAL", "Unremarkable mediastinal contour and hilar vascular structures.", List.of()),
                new RegionalFinding("Pleura & Chest Wall", "NORMAL", "No pleural effusion, thickening, or pneumothorax identified.", List.of())
        );

        String impression = "No acute cardiopulmonary abnormality detected. Visualized thoracic structures are unremarkable.";

        return new GroundedReport(
                regions,
                impression,
                "ROUTINE",
                findings.overallConfidence(),
                "Routine clinical follow-up as clinically indicated. No urgent intervention required.",
                "Triage Auto-Summary (High-Confidence Clean Scan)",
                true,
                "Normal Study: Visualized lungs, pleura, and mediastinum are clear without focal abnormality.",
                "Your chest imaging appears clear and healthy, with no signs of pneumonia, fluid, or suspicious nodules detected.",
                "CLINICAL DECISION SUPPORT NOTICE: Lucidia provides AI-assisted second-read CT documentation. It does NOT provide autonomous diagnostic decisions. Documented clinician review is mandatory."
        );
    }

    public static GroundedReport createCleanExternalPhotoSummary(AggregatedFindings findings) {
        List<RegionalFinding> regions = List.of(
                new RegionalFinding("External Skin & Soft Tissue", "NORMAL", "Visualized skin surface appears intact and uniform without discrete focal ulceration, suspicious pigment irregularity, or marked erythema.", List.of())
        );

        String impression = "No discrete focal external pathology or suspicious skin lesion identified on photograph.";

        return new GroundedReport(
                regions,
                impression,
                "ROUTINE",
                findings.overallConfidence(),
                "Routine clinical skin self-examination. In-person clinical review advised if new symptoms, changes, or pain develop.",
                "Triage Auto-Summary (External Clinical Photo)",
                true,
                "Unremarkable External Photo: Surface appears clear without focal cutaneous lesion.",
                "The photographed skin surface looks clear with no obvious concerning spots or sores. Always consult a healthcare provider for any changing or bothersome skin areas.",
                "RESPONSIBLE AI NOTICE: External photograph analysis is an educational and observational aid. It cannot replace in-person physical clinical examination, dermoscopy, or biopsy."
        );
    }
}
