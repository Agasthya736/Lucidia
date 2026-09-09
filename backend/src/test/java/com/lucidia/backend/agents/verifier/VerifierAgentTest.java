package com.lucidia.backend.agents.verifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lucidia.backend.synthesis.GroundedReport;
import com.lucidia.backend.synthesis.RegionalFinding;
import com.lucidia.backend.triage.AggregatedFindings;
import com.lucidia.backend.triage.DetectedLesion;

class VerifierAgentTest {

    private VerifierAgent verifierAgent;

    @BeforeEach
    void setUp() {
        verifierAgent = new VerifierAgent();
    }

    @Test
    void testCleanReportVerificationPasses() {
        AggregatedFindings cleanFindings = new AggregatedFindings(
                5, 0, "NORMAL", 0.94, List.of(), List.of(), Map.of(), "Clean series"
        );

        GroundedReport cleanReport = GroundedReport.createCleanAutoSummary(cleanFindings);

        VerificationResult result = verifierAgent.verify(cleanReport, cleanFindings);

        assertTrue(result.available());
        assertTrue(result.verified());
        assertTrue(result.flags().isEmpty());
    }

    @Test
    void testUngroundedAbnormalityFlagged() {
        // Detector saw zero lesions (clean study)
        AggregatedFindings cleanFindings = new AggregatedFindings(
                5, 0, "NORMAL", 0.92, List.of(), List.of(), Map.of(), "Clean series"
        );

        // But report hallucinates an abnormality in Left Lower Lobe
        GroundedReport hallucinatedReport = new GroundedReport(
                List.of(new RegionalFinding("Left Lower Lobe", "ABNORMAL", "Large necrotic mass 35mm", List.of(2))),
                "Suspected necrotic mass.",
                "URGENT",
                0.92,
                "Immediate biopsy",
                "Mock",
                false
        );

        VerificationResult result = verifierAgent.verify(hallucinatedReport, cleanFindings);

        assertTrue(result.available());
        assertFalse(result.verified());
        assertFalse(result.flags().isEmpty());
        assertTrue(result.flags().stream().anyMatch(f -> f.contains("Ungrounded abnormality")));
    }

    @Test
    void testOmissionOfSignificantFindingFlagged() {
        // Detector found a high-confidence 14mm nodule in Right Upper Lobe
        DetectedLesion lesion = new DetectedLesion(
                "Pulmonary nodule", "Right Upper Lobe", List.of(100, 100, 200, 200),
                0.93, 40.0, 14.0, "Subpleural nodule"
        );
        AggregatedFindings findings = new AggregatedFindings(
                5, 1, "ABNORMAL", 0.91, List.of(), List.of(lesion),
                Map.of("Right Upper Lobe", List.of(lesion)), "Abnormal finding"
        );

        // But report omits it and only discusses Left Lung
        GroundedReport omittingReport = new GroundedReport(
                List.of(new RegionalFinding("Left Lung", "NORMAL", "Clear parenchyma", List.of())),
                "No significant abnormality detected.",
                "ROUTINE",
                0.91,
                "Routine follow-up",
                "Mock",
                false
        );

        VerificationResult result = verifierAgent.verify(omittingReport, findings);

        assertFalse(result.verified());
        assertTrue(result.flags().stream().anyMatch(f -> f.contains("Missing finding")));
        assertTrue(result.flags().stream().anyMatch(f -> f.contains("Severity discrepancy")));
    }
}
