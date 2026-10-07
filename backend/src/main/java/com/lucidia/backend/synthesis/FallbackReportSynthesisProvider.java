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
        if (findings.findingsByRegion().isEmpty() || findings.abnormalSlicesCount() == 0) {
            return GroundedReport.createCleanAutoSummary(findings);
        }

        boolean isExternalPhoto = findings.findingsByRegion().keySet().stream()
                .anyMatch(r -> r.toLowerCase().contains("cutaneous") || r.toLowerCase().contains("external") || r.toLowerCase().contains("soft tissue"));

        if (isExternalPhoto) {
            return createExternalPhotoReport(findings);
        }

        return createCtRadiologyReport(findings);
    }

    private static GroundedReport createExternalPhotoReport(AggregatedFindings findings) {
        List<RegionalFinding> regionalFindings = new ArrayList<>();
        DetectedLesion top = !findings.topLesions().isEmpty() ? findings.topLesions().get(0) : null;

        double sizeMm = top != null ? top.sizeMm() : 6.0;
        String lesionType = top != null ? top.lesionType() : "Focal cutaneous lesion";
        String region = top != null ? top.anatomicalRegion() : "External Cutaneous / Visible Soft Tissue";
        String desc = top != null ? top.description() : String.format("Photographic evaluation demonstrates a %s measuring approximately %.1f mm with circumscribed margins.", lesionType.toLowerCase(), sizeMm);

        regionalFindings.add(new RegionalFinding(
                region,
                "ABNORMAL",
                desc,
                List.of(0)
        ));

        String conditionGuess = "Possible " + lesionType.toLowerCase();
        String impression = String.format("A %s was spotted on the %s.",
                lesionType.toLowerCase(), region.toLowerCase());

        String severity = sizeMm >= 15.0 ? "URGENT" : (sizeMm >= 6.0 ? "FOLLOW_UP_RECOMMENDED" : "ROUTINE");

        String recommendations;
        if (lesionType.toLowerCase().contains("verruca") || lesionType.toLowerCase().contains("wart") || lesionType.toLowerCase().contains("verrucous")) {
            recommendations =
                    "1. Schedule a routine in-person consultation with a dermatologist or general physician.\n" +
                    "2. Avoid picking, scratching, or cutting the area to prevent irritation or spread.\n" +
                    "3. Keep the skin clean and dry.";
        } else {
            recommendations =
                    "1. Have a doctor or dermatologist inspect the area during a regular checkup.\n" +
                    "2. Watch for any changes in color, size, or shape over time.\n" +
                    "3. Avoid harsh chemicals or picking at the spot.";
        }

        String executiveSummary = conditionGuess + " on " + region;

        String patientFriendly =
                "An area of interest was identified on the " + region.toLowerCase() + " that looks like a " + lesionType.toLowerCase() + ". " +
                "This is an automated AI observation to help you explain what you see. We recommend showing it to a doctor or dermatologist for a proper in-person evaluation.";

        String responsibleAiNotice =
                "AI SAFETY DISCLAIMER: This analysis is AI-generated for informational guidance only and is NOT a medical diagnosis. Please consult a qualified doctor or healthcare professional.";

        return new GroundedReport(
                regionalFindings,
                impression,
                severity,
                findings.overallConfidence(),
                recommendations,
                "Deterministic Grounded Synthesis (External Surface Engine)",
                false,
                executiveSummary,
                patientFriendly,
                responsibleAiNotice
        );
    }

    private static GroundedReport createCtRadiologyReport(AggregatedFindings findings) {
        List<RegionalFinding> regionalFindings = new ArrayList<>();

        for (Map.Entry<String, List<DetectedLesion>> entry : findings.findingsByRegion().entrySet()) {
            String regionName = entry.getKey();
            List<DetectedLesion> lesions = entry.getValue();

            StringBuilder desc = new StringBuilder();
            List<Integer> sliceIdxs = new ArrayList<>();

            for (DetectedLesion l : lesions) {
                desc.append(String.format("Focal %s measuring approximately %.1f mm (attenuation ~%.0f HU) in the %s. ",
                        l.lesionType().toLowerCase(), l.sizeMm(), l.densityHu(), regionName));
            }

            regionalFindings.add(new RegionalFinding(
                    regionName,
                    "ABNORMAL",
                    desc.toString().trim(),
                    sliceIdxs
            ));
        }

        if (!findings.findingsByRegion().containsKey("Mediastinum & Hila")) {
            regionalFindings.add(new RegionalFinding(
                    "Mediastinum & Hila",
                    "NORMAL",
                    "Unremarkable mediastinal contour and hilar vascular structures; no gross adenopathy.",
                    List.of()
            ));
        }
        if (!findings.findingsByRegion().containsKey("Pleura & Chest Wall")) {
            regionalFindings.add(new RegionalFinding(
                    "Pleura & Chest Wall",
                    "NORMAL",
                    "Clear pleural spaces bilaterally; no effusion or pneumothorax.",
                    List.of()
            ));
        }

        DetectedLesion top = findings.topLesions().get(0);
        String conditionGuess = "Possible " + top.lesionType().toLowerCase();
        String impression = String.format("Findings suggest a %s in the %s.",
                top.lesionType().toLowerCase(), top.anatomicalRegion());

        String severity;
        String recommendations;
        if (top.sizeMm() >= 15.0 || top.confidence() > 0.92) {
            severity = "URGENT";
            recommendations =
                    "1. Schedule a prompt in-person appointment with a specialist or your primary care physician.\n" +
                    "2. Take this scan and report to your doctor for physical correlation.\n" +
                    "3. Monitor for any breathing changes or cough.";
        } else if (top.sizeMm() >= 6.0) {
            severity = "FOLLOW_UP_RECOMMENDED";
            recommendations =
                    "1. Discuss this result with your doctor at your next scheduled visit.\n" +
                    "2. Your physician may recommend a routine follow-up scan in 3 to 6 months to ensure stability.\n" +
                    "3. Mention any recent illness, fever, or history to your healthcare provider.";
        } else {
            severity = "ROUTINE";
            recommendations =
                    "1. Routine annual health checkup.\n" +
                    "2. Share with your doctor during standard preventive visits.";
        }

        String executiveSummary = conditionGuess + " in " + top.anatomicalRegion();

        String patientFriendly = String.format("An area of interest was spotted in the %s that looks like a %s. " +
                "This is an automated AI observation and not a medical diagnosis. Please consult a qualified doctor to evaluate these findings in person.",
                top.anatomicalRegion(), top.lesionType().toLowerCase());

        String responsibleAiNotice =
                "AI SAFETY DISCLAIMER: This analysis is AI-generated for informational guidance only and is NOT a medical diagnosis. Please consult a qualified doctor or healthcare professional.";

        return new GroundedReport(
                regionalFindings,
                impression,
                severity,
                findings.overallConfidence(),
                recommendations,
                "Deterministic Grounded Synthesis (Rule Engine)",
                false,
                executiveSummary,
                patientFriendly,
                responsibleAiNotice
        );
    }
}
