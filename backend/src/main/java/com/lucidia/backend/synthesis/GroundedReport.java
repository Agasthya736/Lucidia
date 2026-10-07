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
        String responsibleAiNotice,
        List<PossibleCondition> possibleConditions
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
                "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional.",
                List.of()
        );
    }

    public GroundedReport(
            List<RegionalFinding> clinicalFindings,
            String impression,
            String severity,
            double detectorConfidence,
            String recommendations,
            String generatedBy,
            boolean isAutoSummary,
            String executiveSummary,
            String patientFriendlySummary,
            String responsibleAiNotice) {
        this(
                clinicalFindings,
                impression,
                severity,
                detectorConfidence,
                recommendations,
                generatedBy,
                isAutoSummary,
                executiveSummary,
                patientFriendlySummary,
                responsibleAiNotice,
                List.of()
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
                findings != null ? findings.overallConfidence() : 0.88,
                "Routine follow-up as appropriate. If you experience symptoms, consult a doctor.",
                "Triage Auto-Summary",
                true,
                "No focal features detected across visualized chest areas.",
                "The tool did not detect the features it looks for. This is NOT a clearance. If you have symptoms, see a doctor.",
                "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional.",
                List.of()
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
                findings != null ? findings.overallConfidence() : 0.88,
                "Routine skin observation. Please see a doctor if you notice changing spots, pain, or irritation.",
                "Triage Auto-Summary",
                true,
                "No focal skin features detected on the photograph.",
                "The tool did not detect the features it looks for. This is NOT a clearance. If you have symptoms, see a doctor.",
                "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional.",
                List.of()
        );
    }

    public static GroundedReport createTemplatedFallbackReport(AggregatedFindings findings) {
        String band = findings != null ? findings.resultBand() : "INCONCLUSIVE";
        String bandMsg = findings != null ? findings.bandMessage() : "Could not assess this image. Please see a doctor if you are concerned.";
        double conf = findings != null ? findings.overallConfidence() : 0.50;

        return new GroundedReport(
                List.of(),
                bandMsg,
                "FINDINGS_DETECTED".equals(band) ? "FOLLOW_UP_RECOMMENDED" : "ROUTINE",
                conf,
                "FINDINGS_DETECTED".equals(band)
                        ? "Please see a doctor or qualified healthcare professional."
                        : "Consult a healthcare professional if you have concerns or symptoms.",
                "Templated Fallback Summary",
                true,
                bandMsg,
                bandMsg,
                "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional.",
                List.of()
        );
    }
}
