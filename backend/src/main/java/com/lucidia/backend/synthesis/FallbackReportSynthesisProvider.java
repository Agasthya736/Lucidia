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
                "FINDINGS_DETECTED",
                desc,
                List.of(0)
        ));

        String impression = "The tool detected features that may need attention. See a doctor.";

        String severity = sizeMm >= 15.0 ? "URGENT" : (sizeMm >= 6.0 ? "FOLLOW_UP_RECOMMENDED" : "ROUTINE");

        String recommendations =
                "1. Schedule an appointment with a healthcare professional or dermatologist for an in-person evaluation.\n" +
                "2. Avoid scratching, picking, or irritating the area.\n" +
                "3. Keep the area clean and observe for any changes over time.";

        String executiveSummary = "Observed " + lesionType + " on the " + region;

        String patientFriendly =
                "An area of interest was identified on the " + region.toLowerCase() + " (" + lesionType + "). " +
                "The tool detected features that may need attention. Please see a doctor for an in-person evaluation.";

        String responsibleAiNotice =
                "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional.";

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
                desc.append(String.format("Focal %s observed in the %s. ",
                        l.lesionType().toLowerCase(), regionName));
            }

            regionalFindings.add(new RegionalFinding(
                    regionName,
                    "FINDINGS_DETECTED",
                    desc.toString().trim(),
                    sliceIdxs
            ));
        }

        if (!findings.findingsByRegion().containsKey("Mediastinum & Hila")) {
            regionalFindings.add(new RegionalFinding(
                    "Mediastinum & Hila",
                    "NO_FINDINGS_DETECTED",
                    "Unremarkable mediastinal contour and hilar structures.",
                    List.of()
            ));
        }
        if (!findings.findingsByRegion().containsKey("Pleura & Chest Wall")) {
            regionalFindings.add(new RegionalFinding(
                    "Pleura & Chest Wall",
                    "NO_FINDINGS_DETECTED",
                    "Clear pleural spaces bilaterally.",
                    List.of()
            ));
        }

        DetectedLesion top = findings.topLesions().get(0);
        String impression = "The tool detected features that may need attention. See a doctor.";

        String severity;
        String recommendations;
        if (top.sizeMm() >= 15.0 || top.confidence() > 0.92) {
            severity = "URGENT";
            recommendations =
                    "1. Schedule a prompt in-person appointment with a doctor or specialist.\n" +
                    "2. Take this scan and report to your doctor for clinical correlation.\n" +
                    "3. Monitor for any breathing changes or cough.";
        } else if (top.sizeMm() >= 6.0) {
            severity = "FOLLOW_UP_RECOMMENDED";
            recommendations =
                    "1. Discuss this result with your doctor at your next visit.\n" +
                    "2. Your physician may recommend a follow-up scan in 3 to 6 months to ensure stability.\n" +
                    "3. Mention any symptoms to your healthcare provider.";
        } else {
            severity = "ROUTINE";
            recommendations =
                    "1. Routine health checkup.\n" +
                    "2. Share with your doctor during standard preventive visits.";
        }

        String executiveSummary = "Observed " + top.lesionType().toLowerCase() + " in " + top.anatomicalRegion();

        String patientFriendly = String.format("An area of interest was spotted in the %s (%s). " +
                "The tool detected features that may need attention. Please see a doctor to evaluate these findings in person.",
                top.anatomicalRegion(), top.lesionType().toLowerCase());

        String responsibleAiNotice =
                "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional.";

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
