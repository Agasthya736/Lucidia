package com.lucidia.backend.scan;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
            new Font(Font.HELVETICA, 20, Font.BOLD, Color.decode("#1A2338"));

    private static final Font SUBTITLE_FONT =
            new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);

    private static final Font SECTION_FONT =
            new Font(Font.HELVETICA, 13, Font.BOLD, Color.decode("#1A2338"));

    private static final Font BODY_FONT =
            new Font(Font.HELVETICA, 11, Font.NORMAL);

    private static final Font META_LABEL_FONT =
            new Font(Font.HELVETICA, 10, Font.BOLD);

    private static final Font META_VALUE_FONT =
            new Font(Font.HELVETICA, 10, Font.NORMAL);

    private static final Font WARNING_FONT =
            new Font(Font.HELVETICA, 11, Font.BOLDITALIC,
                    Color.decode("#B91C1C"));

    private static final Font SUCCESS_FONT =
            new Font(Font.HELVETICA, 10, Font.BOLD,
                    Color.decode("#0E7C6B"));

    private static final Font FOOTER_FONT =
            new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] generate(Scan scan) throws IOException {

        Document document =
                new Document(PageSize.A4, 50, 50, 50, 50);

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try {

            PdfWriter.getInstance(document, output);

            document.open();

            addLetterhead(document);

            addPatientMeta(document, scan);

            addHorizontalDivider(document);

            addReportBody(document, scan);

            addHorizontalDivider(document);

            addVerificationFooter(document, scan);

            addSignatureBlock(document, scan);

            document.close();

            return output.toByteArray();

        } catch (DocumentException e) {
            throw new IOException("Unable to generate PDF report", e);
        }
    }

    private void addLetterhead(Document document)
            throws DocumentException {

        Paragraph title =
                new Paragraph("LUCIDIA", TITLE_FONT);

        title.setAlignment(Element.ALIGN_CENTER);

        document.add(title);

        Paragraph subtitle =
                new Paragraph(
                        "AI Assisted Multi-Agent Radiology Reporting Platform",
                        SUBTITLE_FONT);

        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(8);

        document.add(subtitle);

        addHorizontalDivider(document);

        document.add(Chunk.NEWLINE);
    }

    private void addPatientMeta(Document document, Scan scan)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);

        table.setWidthPercentage(100);

        table.setSpacingAfter(15);

        String createdDate =
                scan.getCreatedAt()
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern(
                                "MMMM d, yyyy  h:mm a"));

        addMetaRow(
                table,
                "Report ID",
                scan.getId().toString().substring(0, 8).toUpperCase());

        addMetaRow(
                table,
                "Study Date",
                createdDate);

        addMetaRow(
                table,
                "Image File",
                scan.getImageFilename());

        addMetaRow(
                table,
                "Status",
                scan.getStatus().name());

        document.add(table);
    }

    private void addMetaRow(
            PdfPTable table,
            String label,
            String value) {

        PdfPCell left =
                new PdfPCell(
                        new Phrase(label, META_LABEL_FONT));

        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingBottom(5);

        table.addCell(left);

        PdfPCell right =
                new PdfPCell(
                        new Phrase(
                                value == null ? "N/A" : value,
                                META_VALUE_FONT));

        right.setBorder(Rectangle.NO_BORDER);
        right.setPaddingBottom(5);

        table.addCell(right);
    }

    private void addHorizontalDivider(Document document)
            throws DocumentException {

        LineSeparator separator =
                new LineSeparator();

        separator.setLineColor(Color.decode("#4FD1C5"));

        document.add(new Chunk(separator));

        document.add(Chunk.NEWLINE);
    }
        private void addReportBody(Document document, Scan scan)
            throws DocumentException, IOException {

        document.add(new Paragraph("FINDINGS", SECTION_FONT));

        if (scan.getReportJson() == null || scan.getReportJson().isBlank()) {

            document.add(new Paragraph(
                    "No report has been generated yet.",
                    BODY_FONT));

            document.add(Chunk.NEWLINE);
            return;
        }

        JsonNode report = objectMapper.readTree(scan.getReportJson());

        String findings =
                report.path("findings")
                        .asText("No findings available.");

        String impression =
                report.path("impression")
                        .asText("No impression available.");

        document.add(new Paragraph(findings, BODY_FONT));

        document.add(Chunk.NEWLINE);

        document.add(new Paragraph("IMPRESSION", SECTION_FONT));

        document.add(new Paragraph(impression, BODY_FONT));

        document.add(Chunk.NEWLINE);

        /*
         * Future Integration
         *
         * Add ViT Summary
         * Add Gemini Summary
         * Add Qwen Summary
         * Add Arbiter Consensus
         */

        if (report.path("flaggedForReview").asBoolean(false)) {

            document.add(new Paragraph(
                    "⚠ FLAGGED FOR CLINICIAN REVIEW",
                    WARNING_FONT));

            document.add(Chunk.NEWLINE);
        }
    }

    private void addVerificationFooter(
            Document document,
            Scan scan)
            throws DocumentException, IOException {

        document.add(new Paragraph(
                "VERIFICATION",
                SECTION_FONT));

        if (scan.getVerificationJson() == null ||
                scan.getVerificationJson().isBlank()) {

            document.add(new Paragraph(
                    "Verification unavailable.",
                    BODY_FONT));

            document.add(Chunk.NEWLINE);

            return;
        }

        JsonNode verification =
                objectMapper.readTree(scan.getVerificationJson());

        boolean verified =
                verification.path("verified").asBoolean(false);

        String notes =
                verification.path("notes")
                        .asText("No verification notes.");

        String confidence =
                verification.has("confidence")
                        ? verification.get("confidence").asText()
                        : "N/A";

        document.add(new Paragraph(
                "Verified : " + (verified ? "YES" : "NO"),
                BODY_FONT));

        document.add(new Paragraph(
                "Confidence : " + confidence,
                BODY_FONT));

        document.add(new Paragraph(
                "Notes",
                SECTION_FONT));

        document.add(new Paragraph(
                notes,
                BODY_FONT));

        document.add(Chunk.NEWLINE);
    }

    private void addSignatureBlock(
            Document document,
            Scan scan)
            throws DocumentException {

        addHorizontalDivider(document);

        if (scan.getStatus() == Scan.Status.FINALIZED) {

            String finalizedDate =
                    scan.getFinalizedAt() == null
                            ? "N/A"
                            : scan.getFinalizedAt()
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern(
                                    "MMMM d, yyyy  h:mm a"));

            document.add(new Paragraph(
                    "✓ Clinician Sign-off Completed",
                    SUCCESS_FONT));

            document.add(new Paragraph(
                    "Finalized On : " + finalizedDate,
                    BODY_FONT));

        } else {

            document.add(new Paragraph(
                    "PENDING CLINICIAN SIGN-OFF",
                    WARNING_FONT));
        }

        document.add(Chunk.NEWLINE);

        Paragraph disclaimer =
                new Paragraph(
                        "This report was generated using the Lucidia "
                                + "Multi-Agent AI pipeline. The report is intended "
                                + "to assist clinicians and must not be considered "
                                + "an autonomous medical diagnosis.",
                        FOOTER_FONT);

        disclaimer.setAlignment(Element.ALIGN_JUSTIFIED);

        document.add(disclaimer);

        /*
         * ============================
         * Future Enhancements
         * ============================
         *
         * □ Hospital Logo
         * □ QR Code
         * □ Digital Signature
         * □ SHA-256 Audit Hash
         * □ ViT Summary
         * □ Gemini Summary
         * □ Qwen Summary
         * □ Arbiter Consensus
         * □ Knowledge Agent Output
         * □ Report Version
         *
         */
    }

}