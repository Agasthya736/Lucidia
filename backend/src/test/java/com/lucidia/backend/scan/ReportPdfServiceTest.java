package com.lucidia.backend.scan;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportPdfServiceTest {

    private ReportPdfService pdfService;

    @BeforeEach
    void setUp() {
        pdfService = new ReportPdfService();
    }

    private static boolean isPdfHeaderValid(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 5) return false;
        // Check for PDF magic header %PDF-
        return pdfBytes[0] == '%' && pdfBytes[1] == 'P' && pdfBytes[2] == 'D' && pdfBytes[3] == 'F' && pdfBytes[4] == '-';
    }

    @Test
    void testGeneratePdfForCleanScan() throws IOException {
        Scan scan = new Scan(UUID.randomUUID(), "study_series_clean.png", 5, "hash12345");
        scan.setStatus(Scan.Status.COMPLETED);

        String triageJson = """
        {
            "totalSlices": 5,
            "abnormalSlicesCount": 0,
            "overallStatus": "NO_FINDINGS_DETECTED",
            "overallConfidence": 0.94,
            "summaryEvidence": "All 5 slices demonstrate homogeneous thoracic parenchyma without focal hyperdense contours.",
            "topLesions": []
        }
        """;
        scan.setTriageJson(triageJson);

        String reportJson = """
        {
            "clinicalFindings": [
                {"region": "Lungs / Parenchyma", "status": "NO_FINDINGS_DETECTED", "description": "Lungs are clear bilaterally. No focal consolidations or nodules.", "sliceIndices": [0, 1, 2, 3, 4]},
                {"region": "Mediastinum & Heart", "status": "NO_FINDINGS_DETECTED", "description": "Mediastinal contours unremarkable.", "sliceIndices": [2, 3]}
            ],
            "impression": "No significant abnormality detected.",
            "severity": "ROUTINE",
            "recommendations": "No acute thoracic imaging intervention required."
        }
        """;
        scan.setReportJson(reportJson);

        String verificationJson = """
        {
            "verified": true,
            "flags": [],
            "notes": "All claims strictly grounded in clean CT triage detector telemetry.",
            "groundingScore": 1.0
        }
        """;
        scan.setVerificationJson(verificationJson);

        byte[] pdf = pdfService.generate(scan);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500, "Generated PDF should have substantive content");
        assertTrue(isPdfHeaderValid(pdf), "Output should start with %PDF header");
    }

    @Test
    void testGeneratePdfForFinalizedScanWithSignOff() throws IOException {
        Scan scan = new Scan(UUID.randomUUID(), "study_abnormal_series.png", 8, "hash98765");
        scan.setStatus(Scan.Status.FINALIZED);
        scan.setEscalated(true);
        scan.setReviewerName("Dr. Sarah Chen, MD");
        scan.setReviewerCredentials("Board Certified Radiologist, Lic #RAD-94821");
        scan.setSignOffNotes("Confirmed 7.8mm pulmonary nodule in RUL. Recommended 3-month low-dose follow-up CT.");
        scan.setFinalizedAt(Instant.now());

        String triageJson = """
        {
            "totalSlices": 8,
            "abnormalSlicesCount": 2,
            "overallStatus": "FINDINGS_DETECTED",
            "overallConfidence": 0.89,
            "summaryEvidence": "Identified focal hyperdense nodular mass in Right Upper Lobe on slices 2 and 3.",
            "topLesions": [
                {
                    "sliceIndex": 2,
                    "anatomicalRegion": "Right Upper Lobe",
                    "lesionType": "Pulmonary Nodule",
                    "confidence": 0.89,
                    "densityHu": 42.5,
                    "sizeMm": 7.8,
                    "boundingBox": [410, 320, 480, 395],
                    "description": "Circumscribed soft-tissue density nodule"
                }
            ]
        }
        """;
        scan.setTriageJson(triageJson);

        String reportJson = """
        {
            "clinicalFindings": [
                {"region": "Right Upper Lobe", "status": "FINDINGS_DETECTED", "description": "7.8mm circumscribed hyperdense nodule visualized in the posterior segment.", "sliceIndices": [2, 3]},
                {"region": "Left Lung", "status": "NO_FINDINGS_DETECTED", "description": "Unremarkable aeration.", "sliceIndices": [0, 1, 2, 3, 4, 5, 6, 7]},
                {"region": "Mediastinum & Pleura", "status": "NO_FINDINGS_DETECTED", "description": "No lymphadenopathy or effusion.", "sliceIndices": [3, 4]}
            ],
            "impression": "Suspected 7.8mm solitary pulmonary nodule, right upper lobe.",
            "severity": "FOLLOW_UP_RECOMMENDED",
            "recommendations": "Recommend interval low-dose chest CT in 3 to 6 months per Fleischner criteria."
        }
        """;
        scan.setReportJson(reportJson);

        String verificationJson = """
        {
            "verified": true,
            "flags": [],
            "notes": "Verified against triage detector lesion coordinates in Right Upper Lobe.",
            "groundingScore": 1.0
        }
        """;
        scan.setVerificationJson(verificationJson);

        byte[] pdf = pdfService.generate(scan);

        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "Signed-off multi-slice PDF should contain comprehensive report data");
        assertTrue(isPdfHeaderValid(pdf), "Output should start with %PDF header");
    }

    @Test
    void testDefensiveHandlingOfNullAndMalformedFields() {
        // Create an empty scan with nulls everywhere
        Scan emptyScan = new Scan(null, null);

        assertDoesNotThrow(() -> {
            byte[] pdf = pdfService.generate(emptyScan);
            assertNotNull(pdf);
            assertTrue(isPdfHeaderValid(pdf));
        }, "PDF service must handle uninitialized scans defensively without NPE");

        // Scan with malformed JSON strings
        emptyScan.setReportJson("{malformed: json");
        emptyScan.setTriageJson("not json at all");
        emptyScan.setVerificationJson("true");

        assertDoesNotThrow(() -> {
            byte[] pdf = pdfService.generate(emptyScan);
            assertNotNull(pdf);
            assertTrue(isPdfHeaderValid(pdf));
        }, "PDF service must gracefully recover from malformed JSON payloads");
    }
}
