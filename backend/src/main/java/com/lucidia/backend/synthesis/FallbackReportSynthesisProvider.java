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

        String impression = String.format("%s (approx. %.1f mm) involving %s. Correlate with clinical examination.",
                lesionType, sizeMm, region);

        String severity = sizeMm >= 15.0 ? "URGENT" : (sizeMm >= 6.0 ? "FOLLOW_UP_RECOMMENDED" : "ROUTINE");

        String recommendations;
        if (lesionType.toLowerCase().contains("verruca") || lesionType.toLowerCase().contains("verrucous")) {
            recommendations =
                    "1. Outpatient clinical or dermatological evaluation for definitive diagnosis and treatment options (e.g., cryotherapy, topical salicylic acid/cantharidin, or keratolytic therapy).\n" +
                    "2. Avoid picking, biting, or self-excising the lesion to prevent secondary bacterial infection or auto-inoculation.\n" +
                    "3. Keep the area clean and dry. Seek prompt medical care if surrounding erythema, warmth, or purulent drainage develops.";
        } else {
            recommendations =
                    "1. In-person clinical physical examination and dermoscopic assessment by a qualified physician or dermatologist.\n" +
                    "2. Monitor for warning signs (ABCDE criteria: Asymmetry, Border irregularity, Color changes, Diameter >6mm, Evolution/change).\n" +
                    "3. Avoid topical irritants, scratching, or self-excision.";
        }

        String executiveSummary = String.format("%s (approx. %.1f mm) in %s. Clinical evaluation advised.",
                lesionType, sizeMm, region);

        String patientFriendly =
                "A visible lesion measuring around " + sizeMm + " mm was identified on the " + region.toLowerCase() + ". " +
                "Please have a physician or dermatologist look at it in person to confirm diagnosis and advise on appropriate care.";

        String responsibleAiNotice =
                "RESPONSIBLE AI NOTICE: External clinical photograph evaluation is an automated visual screening aid only. " +
                "It cannot replace direct in-person physical clinical examination, dermoscopy, or biopsy.";

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
        String impression = String.format("Suspected %s (approx. %.1f mm) in the %s.",
                top.lesionType().toLowerCase(), top.sizeMm(), top.anatomicalRegion());

        String severity;
        String recommendations;
        if (top.sizeMm() >= 15.0 || top.confidence() > 0.92) {
            severity = "URGENT";
            recommendations =
                    "1. Prompt multidisciplinary pulmonary / clinical evaluation.\n" +
                    "2. Diagnostic contrast-enhanced chest CT recommended for precise tissue staging.\n" +
                    "3. Correlate with previous imaging to assess interval growth rate.";
        } else if (top.sizeMm() >= 6.0) {
            severity = "FOLLOW_UP_RECOMMENDED";
            recommendations =
                    "1. Follow-up low-dose non-contrast chest CT in 3 to 6 months per Fleischner Society pulmonary nodule guidelines.\n" +
                    "2. Retrieve and compare with any prior chest radiographs or CT studies.\n" +
                    "3. Clinical correlation with patient smoking history and pulmonary symptoms.";
        } else {
            severity = "ROUTINE";
            recommendations =
                    "1. Clinical correlation; consider routine follow-up CT at 12 months for high-risk clinical profiles.\n" +
                    "2. Standard preventive health monitoring.";
        }

        String executiveSummary = String.format("Abnormality noted: %.1f mm %s in %s. %s",
                top.sizeMm(), top.lesionType().toLowerCase(), top.anatomicalRegion(),
                severity.equals("URGENT") ? "Prompt clinical attention advised." : "Follow-up imaging recommended.");

        String patientFriendly = String.format("A small spot (approx. %.1f mm) was identified in the %s. " +
                "Your doctor will likely suggest a follow-up scan in a few months to verify that it remains unchanged and stable.",
                top.sizeMm(), top.anatomicalRegion());

        String responsibleAiNotice =
                "CLINICAL DECISION SUPPORT NOTICE: Lucidia provides AI-assisted second-read CT documentation. " +
                "It does NOT provide autonomous diagnostic decisions. Documented clinician review is mandatory.";

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
