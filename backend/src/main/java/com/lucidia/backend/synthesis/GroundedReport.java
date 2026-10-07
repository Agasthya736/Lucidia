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
                "EDUCATIONAL USE NOTICE: Lucidia provides AI-assisted image analysis for informational purposes only. Always consult a qualified healthcare professional for medical decisions."
        );
    }

    public static GroundedReport createCleanAutoSummary(AggregatedFindings findings) {
        List<RegionalFinding> regions = List.of(
                new RegionalFinding("Right Lung", "NO_FINDINGS_DETECTED", "No focal pulmonary features or discrete opacities detected.", List.of()),
                new RegionalFinding("Left Lung", "NO_FINDINGS_DETECTED", "No focal pulmonary features or discrete opacities detected.", List.of()),
                new RegionalFinding("Mediastinum & Hila", "NO_FINDINGS_DETECTED", "Unremarkable mediastinal contour and hilar structures.", List.of()),
                new RegionalFinding("Pleura & Chest Wall", "NO_FINDINGS_DETECTED", "No pleural fluid collection or thickening detected.", List.of())
        );

        String impression = "The tool did not detect the features it looks for. This is NOT a clearance. If you have symptoms, see a doctor.";

        return new GroundedReport(
                regions,
                impression,
                "ROUTINE",
                findings.overallConfidence(),
                "Routine follow-up as appropriate. If you experience symptoms, consult a doctor.",
                "Triage Auto-Summary",
                true,
                "No focal features detected across visualized chest areas.",
                "The tool did not detect the features it looks for. This is NOT a clearance. If you have symptoms, see a doctor.",
                "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional."
        );
    }

    public static GroundedReport createCleanExternalPhotoSummary(AggregatedFindings findings) {
        List<RegionalFinding> regions = List.of(
                new RegionalFinding("External Skin & Soft Tissue", "NO_FINDINGS_DETECTED", "Visualized skin surface appears intact without discrete focal surface changes or marked color variation.", List.of())
        );

        String impression = "The tool did not detect the features it looks for. This is NOT a clearance. If you have symptoms, see a doctor.";

        return new GroundedReport(
                regions,
                impression,
                "ROUTINE",
                findings.overallConfidence(),
                "Routine skin observation. Please see a doctor if you notice changing spots, pain, or irritation.",
                "Triage Auto-Summary",
                true,
                "No focal skin features detected on the photograph.",
                "The tool did not detect the features it looks for. This is NOT a clearance. If you have symptoms, see a doctor.",
                "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional."
        );
    }
}
