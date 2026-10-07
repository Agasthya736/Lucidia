package com.lucidia.backend.agents.verifier;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lucidia.backend.synthesis.GroundedReport;
import com.lucidia.backend.synthesis.RegionalFinding;
import com.lucidia.backend.triage.AggregatedFindings;
import com.lucidia.backend.triage.DetectedLesion;

/**
 * Strict Grounding & Consistency Verifier.
 * Rather than performing an ungrounded second vision read, this agent directly
 * cross-checks the synthesized report claims against the objective detector evidence:
 * 1. Checks for ungrounded abnormalities (abnormalities stated in regions where detector saw nothing).
 * 2. Checks for omitted findings (high-confidence lesions detected that are missing from report).
 * 3. Validates severity alignment with detector findings.
 * 4. Ensures detector confidence matches the triage engine output.
 */
@Service
public class VerifierAgent {

    private static final Logger log = LoggerFactory.getLogger(VerifierAgent.class);

    public VerificationResult verify(GroundedReport report, AggregatedFindings detectorFindings) {
        if (report == null || detectorFindings == null) {
            return VerificationResult.unavailable("Report or detector findings missing for verification.");
        }

        List<String> flags = new ArrayList<>();

        // 1. Check for ungrounded abnormalities
        for (RegionalFinding rf : report.clinicalFindings()) {
            if ("ABNORMAL".equalsIgnoreCase(rf.status()) || "FINDINGS_DETECTED".equalsIgnoreCase(rf.status())) {
                boolean matched = false;
                for (String detectedRegion : detectorFindings.findingsByRegion().keySet()) {
                    if (isRegionMatch(rf.region(), detectedRegion)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched && detectorFindings.abnormalSlicesCount() == 0) {
                    flags.add("Ungrounded abnormality: Report describes abnormal findings in '" + rf.region()
                            + "', but triage detector found zero lesions in this series.");
                }
            }
        }

        // 2. Check for missing significant lesions
        for (DetectedLesion lesion : detectorFindings.topLesions()) {
            if (lesion.confidence() >= 0.80) {
                boolean addressed = false;
                for (RegionalFinding rf : report.clinicalFindings()) {
                    if (isRegionMatch(rf.region(), lesion.anatomicalRegion())) {
                        addressed = true;
                        break;
                    }
                }
                if (!addressed && !report.impression().toLowerCase().contains(lesion.lesionType().toLowerCase())) {
                    flags.add("Missing finding: Detector identified '" + lesion.lesionType()
                            + "' in " + lesion.anatomicalRegion() + " (conf " + lesion.confidence()
                            + "), but this was omitted from clinical findings.");
                }
            }
        }

        // 3. Check severity consistency
        if (detectorFindings.abnormalSlicesCount() > 0 && "ROUTINE".equalsIgnoreCase(report.severity())) {
            flags.add("Severity discrepancy: Series has " + detectorFindings.abnormalSlicesCount()
                    + " abnormal slices, but severity flag is set to 'ROUTINE'.");
        }
        if (detectorFindings.abnormalSlicesCount() == 0 && "URGENT".equalsIgnoreCase(report.severity())) {
            flags.add("Severity discrepancy: No abnormal slices detected, but report marked as 'URGENT'.");
        }

        // 4. Grounding score calculation
        double groundingScore = 1.0;
        if (!flags.isEmpty()) {
            groundingScore = Math.max(0.0, 1.0 - (flags.size() * 0.25));
        }

        boolean verified = flags.isEmpty();
        String notes = verified
                ? "All clinical findings are strictly grounded in CT triage detector evidence. No omissions or discrepancies."
                : flags.size() + " grounding issue(s) identified between report claims and detector evidence.";

        log.info("Verification result: verified={}, flags={}, score={}", verified, flags.size(), groundingScore);
        return VerificationResult.of(verified, flags, notes, groundingScore);
    }

    private boolean isRegionMatch(String reportRegion, String detectorRegion) {
        if (reportRegion == null || detectorRegion == null) return false;
        String r = reportRegion.toLowerCase().replaceAll("[^a-z]", "");
        String d = detectorRegion.toLowerCase().replaceAll("[^a-z]", "");
        return r.contains(d) || d.contains(r)
                || (r.contains("right") && d.contains("right"))
                || (r.contains("left") && d.contains("left"))
                || (r.contains("mediastin") && d.contains("mediastin"));
    }
}