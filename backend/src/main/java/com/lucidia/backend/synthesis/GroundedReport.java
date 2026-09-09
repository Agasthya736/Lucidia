package com.lucidia.backend.synthesis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.lucidia.backend.triage.AggregatedFindings;

public record GroundedReport(
        List<RegionalFinding> clinicalFindings,
        String impression,
        String severity, // ROUTINE, FOLLOW_UP_RECOMMENDED, URGENT
        double detectorConfidence,
        String recommendations,
        String generatedBy,
        boolean isAutoSummary
) {
    public static GroundedReport createCleanAutoSummary(AggregatedFindings findings) {
        List<RegionalFinding> regions = List.of(
                new RegionalFinding("Right Lung", "NORMAL", "Normal lung parenchyma without focal consolidation, nodule, or mass.", List.of()),
                new RegionalFinding("Left Lung", "NORMAL", "Clear parenchyma without infiltrates or suspicious opacities.", List.of()),
                new RegionalFinding("Mediastinum & Hila", "NORMAL", "Unremarkable mediastinal contour and hilar vascular structures.", List.of()),
                new RegionalFinding("Pleura & Chest Wall", "NORMAL", "No pleural effusion or pneumothorax identified.", List.of())
        );

        return new GroundedReport(
                regions,
                "No significant abnormality detected.",
                "ROUTINE",
                findings.overallConfidence(),
                "Routine clinical correlation and standard preventive follow-up as indicated.",
                "Triage Auto-Summary (0 LLM Cost - High Confidence Clean Scan)",
                true
        );
    }
}
