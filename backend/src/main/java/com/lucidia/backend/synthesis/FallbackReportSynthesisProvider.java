package com.lucidia.backend.synthesis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lucidia.backend.triage.AggregatedFindings;
import com.lucidia.backend.triage.DetectedLesion;

@Component
public class FallbackReportSynthesisProvider implements ReportSynthesisProvider {

    @Override
    public String getProviderId() {
        return "fallback";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public GroundedReport synthesize(AggregatedFindings findings, String customApiKey) {
        return createGroundedFromDetector(findings);
    }

    public static GroundedReport createGroundedFromDetector(AggregatedFindings findings) {
        List<RegionalFinding> regionalFindings = new ArrayList<>();

        if (findings.findingsByRegion().isEmpty() || findings.abnormalSlicesCount() == 0) {
            return GroundedReport.createCleanAutoSummary(findings);
        }

        // Generate findings region by region based on detector evidence
        for (Map.Entry<String, List<DetectedLesion>> entry : findings.findingsByRegion().entrySet()) {
            String regionName = entry.getKey();
            List<DetectedLesion> lesions = entry.getValue();

            StringBuilder desc = new StringBuilder();
            List<Integer> sliceIdxs = new ArrayList<>();

            for (DetectedLesion l : lesions) {
                desc.append(String.format("Identified %s measuring approximately %.1f mm with mean density %.0f HU (detector confidence %.0f%%). %s ",
                        l.lesionType().toLowerCase(), l.sizeMm(), l.densityHu(), l.confidence() * 100.0, l.description()));
            }

            regionalFindings.add(new RegionalFinding(
                    regionName,
                    "ABNORMAL",
                    desc.toString().trim(),
                    sliceIdxs
            ));
        }

        // Add standard normal regions if not affected
        if (!findings.findingsByRegion().containsKey("Mediastinum / Central Airway")) {
            regionalFindings.add(new RegionalFinding(
                    "Mediastinum & Hila",
                    "NORMAL",
                    "Normal mediastinal contour, unremarkable central airways, no significant adenopathy.",
                    List.of()
            ));
        }
        if (!findings.findingsByRegion().containsKey("Pleura & Chest Wall")) {
            regionalFindings.add(new RegionalFinding(
                    "Pleura & Chest Wall",
                    "NORMAL",
                    "No pleural effusions or pneumothorax identified.",
                    List.of()
            ));
        }

        DetectedLesion topLesion = findings.topLesions().get(0);
        String impression = String.format("Suspected %s (approx. %.1f mm) in the %s.",
                topLesion.lesionType().toLowerCase(), topLesion.sizeMm(), topLesion.anatomicalRegion());

        String severity;
        String recommendations;
        if (topLesion.sizeMm() >= 15 || topLesion.confidence() > 0.90) {
            severity = "URGENT";
            recommendations = "Prompt multidisciplinary clinical evaluation and diagnostic contrast CT recommended.";
        } else if (topLesion.sizeMm() >= 6) {
            severity = "FOLLOW_UP_RECOMMENDED";
            recommendations = "Follow-up non-contrast low-dose chest CT in 3 to 6 months per Fleischner Society guidelines.";
        } else {
            severity = "ROUTINE";
            recommendations = "Clinical correlation; optional follow-up at 12 months if high-risk patient.";
        }

        return new GroundedReport(
                regionalFindings,
                impression,
                severity,
                findings.overallConfidence(),
                recommendations,
                "Deterministic Grounded Synthesis (Rule Engine Fallback)",
                false
        );
    }
}
