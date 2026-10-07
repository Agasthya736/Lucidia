package com.lucidia.backend.scan;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

@Service
public class ReportPdfService {

    private static final Font TITLE_FONT =
            new Font(Font.HELVETICA, 18, Font.BOLD, Color.decode("#0F172A"));

    private static final Font SUBTITLE_FONT =
            new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);

    private static final Font SECTION_FONT =
            new Font(Font.HELVETICA, 12, Font.BOLD, Color.decode("#0F172A"));

    private static final Font SUBSECTION_FONT =
            new Font(Font.HELVETICA, 10, Font.BOLD, Color.decode("#1E293B"));

    private static final Font BODY_FONT =
            new Font(Font.HELVETICA, 10, Font.NORMAL, Color.decode("#334155"));

    private static final Font BULLET_FONT =
            new Font(Font.HELVETICA, 10, Font.NORMAL, Color.decode("#1E293B"));

    private static final Font META_LABEL_FONT =
            new Font(Font.HELVETICA, 9, Font.BOLD, Color.decode("#475569"));

    private static final Font META_VALUE_FONT =
            new Font(Font.HELVETICA, 9, Font.NORMAL, Color.decode("#0F172A"));

    private static final Font URGENT_FONT =
            new Font(Font.HELVETICA, 10, Font.BOLD, Color.decode("#DC2626"));

    private static final Font FOLLOW_UP_FONT =
            new Font(Font.HELVETICA, 10, Font.BOLD, Color.decode("#D97706"));

    private static final Font ROUTINE_FONT =
            new Font(Font.HELVETICA, 10, Font.BOLD, Color.decode("#0D9488"));

    private static final Font SUCCESS_FONT =
            new Font(Font.HELVETICA, 11, Font.BOLD, Color.decode("#0E7C6B"));

    private static final Font FOOTER_FONT =
            new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] generate(Scan scan) throws IOException {
        Document document = new Document(PageSize.A4, 45, 45, 40, 40);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, output);
            document.open();

            addLetterhead(document);
            addPatientStudyMeta(document, scan);
            addHorizontalDivider(document);
            addReportBody(document, scan);
            addHorizontalDivider(document);
            addTriageEvidenceSection(document, scan);
            addHorizontalDivider(document);
            addVerificationSection(document, scan);
            addSignatureBlock(document, scan);

            document.close();
            return output.toByteArray();

        } catch (DocumentException e) {
            throw new IOException("Unable to generate PDF report", e);
        }
    }

    private void addLetterhead(Document document) throws DocumentException {
        Paragraph title = new Paragraph("LUCIDIA \u2014 RADIOLOGY REPORT", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph(
                "AI-Assisted Triage & Second-Read Documentation System",
                SUBTITLE_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(6);
        document.add(subtitle);

        addHorizontalDivider(document);
    }

    private void addPatientStudyMeta(Document document, Scan scan) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);

        String createdDate = scan.getCreatedAt() != null
                ? scan.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy  h:mm a"))
                : "N/A";

        String studyId = scan.getId() != null
                ? (scan.getId().toString().length() >= 8
                ? scan.getId().toString().substring(0, 8).toUpperCase()
                : scan.getId().toString().toUpperCase())
                : "UNKNOWN";

        String filename = scan.getImageFilename() != null ? scan.getImageFilename() : "N/A";

        addMetaRow(table, "Study Reference ID", studyId);
        addMetaRow(table, "Study Date / Time", createdDate);
        addMetaRow(table, "CT Series Modality", "Chest CT (Axial Series)");
        addMetaRow(table, "Total Slices Analyzed", String.valueOf(Math.max(1, scan.getSliceCount())));
        addMetaRow(table, "Primary Image File", filename);
        addMetaRow(table, "Pipeline Escalation", scan.isEscalated() ? "Escalated to Grounded Synthesis" : "Clean Scan Auto-Summary");

        document.add(table);
    }

    private void addMetaRow(PdfPTable table, String label, String value) {
        PdfPCell left = new PdfPCell(new Phrase(label, META_LABEL_FONT));
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingBottom(3);
        table.addCell(left);

        PdfPCell right = new PdfPCell(new Phrase(value == null ? "N/A" : value, META_VALUE_FONT));
        right.setBorder(Rectangle.NO_BORDER);
        right.setPaddingBottom(3);
        table.addCell(right);
    }

    private void addHorizontalDivider(Document document) throws DocumentException {
        LineSeparator separator = new LineSeparator();
        separator.setLineColor(Color.decode("#CBD5E1"));
        document.add(new Chunk(separator));
    }

    private void addReportBody(Document document, Scan scan) throws DocumentException, IOException {
        if (scan.getReportJson() == null || scan.getReportJson().isBlank()) {
            document.add(new Paragraph("No report content available.", BODY_FONT));
            return;
        }

        JsonNode report;
        try {
            report = objectMapper.readTree(scan.getReportJson());
        } catch (Exception e) {
            document.add(new Paragraph("Report format unparseable: " + e.getMessage(), BODY_FONT));
            return;
        }

        // 1. Severity Banner
        String severity = report.path("severity").asText("ROUTINE").toUpperCase();
        Font sevFont = switch (severity) {
            case "URGENT" -> URGENT_FONT;
            case "FOLLOW_UP_RECOMMENDED" -> FOLLOW_UP_FONT;
            default -> ROUTINE_FONT;
        };

        Paragraph sevP = new Paragraph();
        sevP.add(new Chunk("TRIAGE SEVERITY: ", META_LABEL_FONT));
        sevP.add(new Chunk(severity.replace("_", " "), sevFont));
        sevP.setSpacingAfter(8);
        document.add(sevP);

        // 2. Impression
        document.add(new Paragraph("SUSPECTED ABNORMALITY / IMPRESSION", SECTION_FONT));
        String impression = report.path("impression").asText("No significant abnormality detected.");
        Paragraph impP = new Paragraph(impression, BODY_FONT);
        impP.setSpacingAfter(10);
        document.add(impP);

        // 3. Clinical Findings (Region by region)
        document.add(new Paragraph("CLINICAL FINDINGS (BY ANATOMICAL REGION)", SECTION_FONT));
        JsonNode findingsNode = report.path("clinicalFindings");

        if (findingsNode.isArray() && findingsNode.size() > 0) {
            for (JsonNode rf : findingsNode) {
                String region = rf.path("region").asText("Region");
                String status = rf.path("status").asText("NORMAL");
                String desc = rf.path("description").asText("");

                Paragraph regHeader = new Paragraph();
                regHeader.add(new Chunk("• " + region + " (" + status + "): ", SUBSECTION_FONT));
                regHeader.add(new Chunk(desc, BODY_FONT));
                regHeader.setSpacingAfter(4);
                regHeader.setIndentationLeft(8);
                document.add(regHeader);
            }
        } else {
            String rawFindings = report.path("findings").asText("");
            if (!rawFindings.isBlank()) {
                Paragraph p = new Paragraph(rawFindings, BODY_FONT);
                p.setIndentationLeft(8);
                document.add(p);
            } else {
                document.add(new Paragraph("Visualized thoracic anatomy unremarkable.", BODY_FONT));
            }
        }

        // 4. Recommendations
        Paragraph recHeader = new Paragraph("RECOMMENDATIONS", SECTION_FONT);
        recHeader.setSpacingBefore(6);
        document.add(recHeader);
        String recommendations = report.path("recommendations").asText("Routine clinical correlation.");
        Paragraph recP = new Paragraph(recommendations, BODY_FONT);
        recP.setSpacingAfter(8);
        document.add(recP);
    }

    private void addTriageEvidenceSection(Document document, Scan scan) throws DocumentException, IOException {
        document.add(new Paragraph("TRIAGE DETECTOR EVIDENCE", SECTION_FONT));

        if (scan.getTriageJson() == null || scan.getTriageJson().isBlank()) {
            document.add(new Paragraph("Triage detector telemetry unavailable.", BODY_FONT));
            return;
        }

        JsonNode triage;
        try {
            triage = objectMapper.readTree(scan.getTriageJson());
        } catch (Exception e) {
            document.add(new Paragraph("Triage telemetry data unparseable.", BODY_FONT));
            return;
        }

        double confidence = triage.path("overallConfidence").asDouble(0.0);
        int abnormalCount = triage.path("abnormalSlicesCount").asInt(0);
        int totalSlices = triage.path("totalSlices").asInt(scan.getSliceCount());

        Paragraph p = new Paragraph();
        p.add(new Chunk(String.format("Detector Confidence: %.1f%%  |  Total Slices: %d  |  Abnormal Slices: %d",
                confidence * 100.0, totalSlices, abnormalCount), META_VALUE_FONT));
        p.setSpacingAfter(6);
        document.add(p);

        String summaryEvidence = triage.path("summaryEvidence").asText("");
        if (!summaryEvidence.isBlank()) {
            Paragraph sumP = new Paragraph(summaryEvidence, BODY_FONT);
            sumP.setSpacingAfter(6);
            document.add(sumP);
        }
    }

    private void addVerificationSection(Document document, Scan scan) throws DocumentException, IOException {
        document.add(new Paragraph("GROUNDING VERIFICATION", SECTION_FONT));

        if (scan.getVerificationJson() == null || scan.getVerificationJson().isBlank()) {
            document.add(new Paragraph("Verification check unavailable.", BODY_FONT));
            return;
        }

        JsonNode verification;
        try {
            verification = objectMapper.readTree(scan.getVerificationJson());
        } catch (Exception e) {
            document.add(new Paragraph("Verification telemetry unparseable.", BODY_FONT));
            return;
        }

        boolean verified = verification.path("verified").asBoolean(false);
        String notes = verification.path("notes").asText("Verification complete.");

        Paragraph vp = new Paragraph();
        vp.add(new Chunk("Status: " + (verified ? "VERIFIED & GROUNDED" : "FLAGGED ISSUES") + "  \u2014  " + notes, BODY_FONT));
        vp.setSpacingAfter(8);
        document.add(vp);
    }

    private void addSignatureBlock(Document document, Scan scan) throws DocumentException {
        addHorizontalDivider(document);

        if (scan.getStatus() == Scan.Status.FINALIZED) {
            String finalizedDate = scan.getFinalizedAt() == null
                    ? "N/A"
                    : scan.getFinalizedAt()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMMM d, yyyy  h:mm a"));

            String reviewer = scan.getReviewerName() != null && !scan.getReviewerName().isBlank()
                    ? scan.getReviewerName()
                    : "Reviewer";
            String credentials = scan.getReviewerCredentials() != null && !scan.getReviewerCredentials().isBlank()
                    ? scan.getReviewerCredentials()
                    : "";

            Paragraph sigHeader = new Paragraph("REVIEW COMPLETED", SUCCESS_FONT);
            document.add(sigHeader);

            PdfPTable sigTable = new PdfPTable(2);
            sigTable.setWidthPercentage(100);
            sigTable.setSpacingBefore(6);
            sigTable.setSpacingAfter(10);

            addMetaRow(sigTable, "Reviewed By", reviewer);
            if (!credentials.isBlank()) {
                addMetaRow(sigTable, "Credentials", credentials);
            }
            addMetaRow(sigTable, "Review Timestamp", finalizedDate);
            if (scan.getSignOffNotes() != null && !scan.getSignOffNotes().isBlank()) {
                addMetaRow(sigTable, "Notes", scan.getSignOffNotes());
            }

            document.add(sigTable);
        }

        Paragraph disclaimer = new Paragraph(
                "IMPORTANT SAFETY NOTICE: This report is AI-generated for educational and informational guidance only. "
                        + "It is NOT an official medical diagnosis. Please consult a qualified doctor or healthcare specialist "
                        + "for clinical evaluation, prescription, or medical decision-making.",
                FOOTER_FONT);
        disclaimer.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(disclaimer);
    }
}